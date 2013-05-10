package org.neo4j.jdbc.rest;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.jdbc.ExecutionResult;
import org.restlet.representation.Representation;

import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.*;

/**
 * @author mh
 * @since 19.04.13
 */
class StreamingParser {
    private final JsonFactory JSON_FACTORY; // todo static?

    private final ObjectMapper mapper;

    StreamingParser(ObjectMapper mapper) {
        this.mapper = mapper;
        JSON_FACTORY = new JsonFactory(mapper);
    }

    public JsonParser obtainParser(Representation representation) throws SQLException {
        try {
            return obtainParser(representation.getReader());
        } catch (IOException ioe) {
            throw new IllegalStateException("Error creating parser",ioe);
        }
    }

    static class ParserState {
        JsonParser parser;
        JsonToken nextToken;

        private ParserState(JsonParser parser) {
            this.parser = parser;
        }

        JsonToken nextToken() {
            if (nextToken == null) {
                try {
                    nextToken = parser.nextToken();
                } catch (IOException ioe) {
                    throw new IllegalStateException("Error during parsing", ioe);
                }
            }
            return nextToken;
        }

        void consumeLast() {
            nextToken = null;
        }

        static ParserState from(JsonParser parser) {
            return new ParserState(parser);
        }

        public String getCurrentName() {
            try {
                return parser.getCurrentName();
            } catch (IOException e) {
                throw new IllegalStateException("Error during parse");
            }
        }

        public <T> T readValueAs(Class<T> type) {
            try {
                return parser.readValueAs(type);
            } catch (IOException e) {
                throw new IllegalStateException("Error during parse");
            }
        }
    }

    ExecutionResult nextResult(final ParserState state) {
        if (!skipTo(state, JsonToken.START_OBJECT, "columns")) return null;
        final List<String> columns = readList(state);
        final int cols = columns.size();
        skipTo(state, "data", JsonToken.START_ARRAY);
        return new ExecutionResult(columns, new Iterator<Object[]>() {
            boolean last=false;
            Object[] nextRow = null;

            private Object[] nextRow() {
                final Object[] row = readObjectArray(state);
                if (row!=null && row.length != cols) throw new IllegalStateException("Row length "+row.length+" differs from column definition "+columns+" row details "+Arrays.toString(row));
                return row;
            }

            public boolean hasNext() {
                if (last) return false;
                if (nextRow ==null) {
                    nextRow =nextRow();
                    if (nextRow ==null) {
                        last = true;
                        skipTo(state, JsonToken.END_ARRAY, JsonToken.END_OBJECT); // go to end of the result
                    }
                }
                return nextRow != null;
            }

            @Override
            public Object[] next() {
                if (!hasNext()) throw new NoSuchElementException();
                Object[] row = nextRow;
                nextRow = null;
                return row;
            }

            public void remove() { }
        });
    }

    Object[] readObjectArray(ParserState state) {
        final List<Object> objects = readList(state);
        return objects != null ? objects.toArray() : null;
    }

    <T> List<T> readList(ParserState state) {
        try {
            JsonToken token = nextToken(state);
            if (token == JsonToken.START_ARRAY) {
                final List<T> result = (List<T>) state.readValueAs(List.class);
                state.consumeLast();
                return result;
            }
            // TODO if (token != null) throw new IllegalStateException("Unexpected token "+token);
            System.err.println("Unexpected token " + token);
            return null;
        } catch (IOException ioe) {
            throw new IllegalStateException("Unexpected error", ioe);
        }
    }

    Iterator<ExecutionResult> toResults(final JsonParser parser, Statement... statements) throws SQLException {
        try {
            final ParserState state = ParserState.from(parser);
            skipTo(state, JsonToken.START_OBJECT, "results", JsonToken.START_ARRAY); // { "results"
            return new Iterator<ExecutionResult>() {
                boolean last=false;
                ExecutionResult nextResult = null;

                public boolean hasNext() {
                    if (last) return false;
                    if (nextResult==null) {
                        nextResult=nextResult(state);
                        if (nextResult==null) {
                            last = true;
                            skipTo(state, JsonToken.END_ARRAY, JsonToken.END_OBJECT);
                        }
                    }
                    return nextResult != null;
                }

                @Override
                public ExecutionResult next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    ExecutionResult result = nextResult;
                    nextResult = null;
                    return result;
                }

                public void remove() { }
            };
        } catch (Exception ioe) {
            throw new SQLException("Error executing statements " + Statement.toJson(mapper, statements),ioe);
        }
    }

    boolean skipTo(ParserState state, Object... tokenOrField) {
        System.out.println("Skip to " + Arrays.toString(tokenOrField));
        Map<JsonToken, Object> foundTokens = new LinkedHashMap<JsonToken, Object>();
        for (Object expectedToken : tokenOrField) {
            boolean matched;
            do {
                JsonToken token = state.nextToken();
                foundTokens.put(token, state.getCurrentName());
                handleErrors(state);
                state.consumeLast();
                if (token==null) {
                    System.out.println("Match not found, was expected to skip to " + Arrays.toString(tokenOrField) + " found " + foundTokens+" current "+state.getCurrentName());
                    return false;
                }
                System.err.println("next " + token+" "+state.getCurrentName());
                matched = expectedToken == token || state.getCurrentName() != null && state.getCurrentName().equals(expectedToken);
            } while(!matched);
        }
        return true;
    }

    private void handleErrors(ParserState state) {
        if ("errors".equals(state.getCurrentName())) {
            final JsonToken token = state.nextToken();
            System.out.println("errors-next-token = " + token);
            if (token == JsonToken.FIELD_NAME) {
                final List<Object> errors = readList(state);
                state.consumeLast();
                System.out.println(state.parser.getCurrentToken());
                if (errors == null || errors.isEmpty()) return;
                System.out.println("errors = " + errors);
                throw new RuntimeException("Error executing cypher statement(s) "+errors); // todo +state.getStatements()
            }
        }
    }

    public ParserState obtainParserState(Reader reader) throws SQLException {
        return ParserState.from(obtainParser(reader));
    }
    public JsonParser obtainParser(Reader reader) throws SQLException {
        try {
            final JsonParser parser = JSON_FACTORY.createJsonParser(reader);
            parser.setCodec(mapper);
            return parser;
        } catch (IOException ioe) {
            throw new SQLTransientConnectionException("Error creating result parser", ioe);
        }
    }

    public JsonToken nextToken(ParserState state) throws IOException {
        return state.nextToken();
    }
}
