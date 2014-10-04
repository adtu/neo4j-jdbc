package org.neo4j.jdbc.embedded;

import org.junit.Test;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.jdbc.ExecutionResult;
import org.neo4j.test.TestGraphDatabaseFactory;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.neo4j.helpers.collection.MapUtil.map;

/**
 * @author mh
 * @since 15.06.12
 */
public class EmbeddedQueryExecutorTest
{
    @Test
    public void testDoExecuteQuery() throws Exception
    {
        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        long nodeId = createNode( db );
        final EmbeddedQueryExecutor executor = new EmbeddedQueryExecutor( db );
        final ExecutionResult result = executor.executeQuery( "match (n) where id(n) = {1} return ID(n) as id",
                map("1",nodeId), true );
        assertEquals( asList( "id" ), result.columns() );
        final Object[] row = result.iterator().next();
        assertEquals( 1, row.length );
        assertEquals( nodeId, row[0] );
    }

    private long createNode( GraphDatabaseService db )
    {
        try ( Transaction tx = db.beginTx() )
        {
            Node node = db.createNode();
            tx.success();
            return node.getId();
        }
    }
}
