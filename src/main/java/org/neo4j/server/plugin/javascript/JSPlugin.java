/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.plugin.javascript;

import com.tinkerpop.blueprints.pgm.impls.neo4j.Neo4jGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.*;
import org.neo4j.server.rest.repr.ObjectToRepresentationConverter;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.ValueRepresentation;

import javax.script.*;
import java.util.Map;

/* This is a class that will represent a server side
 * Gremlin plugin and will return JSON
 * for the following use cases:
 * Add/delete vertices and edges from the graph.
 * Manipulate the graph indices.
 * Search for elements of a graph.
 * Load graph data from a file or URL.
 * Make use of JUNG algorithms.
 * Make use of SPARQL queries over OpenRDF-based graphs.
 * and much, much more.
 */

@Description( "A server side Javascript plugin for the Neo4j REST server" )
public class JSPlugin extends ServerPlugin
{

    private final String g = "g";
    private final String gdb = "gdb";
    private final String pipe = "pipe";
    
    private volatile ScriptEngine engine;
    private final EngineReplacementDecision engineReplacementDecision = new CountingEngineReplacementDecision( 500 );

    private ScriptEngine createQueryEngine( GraphDatabaseService neo4j )
    {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("rhino-nonjdk");
        
        //final Neo4jGraph graph = new Neo4jGraph( neo4j, false );
    	//final GremlinPipeline pipeline = new GremlinPipeline();        
    	//engine.getContext().setAttribute("g", graph, ScriptContext.ENGINE_SCOPE);
    	//engine.getContext().setAttribute("pipe", pipeline, ScriptContext.ENGINE_SCOPE);

        return engine;
    }

    @Name( "execute_script" )
    @Description( "execute a JavaScript script with 'g' set to the Neo4jGraph and 'results' containing the results. Only results of one object type is supported." )
    @PluginTarget( GraphDatabaseService.class )
    public Representation executeScript(
            @Source final GraphDatabaseService neo4j,
            @Description( "The script" ) @Parameter( name = "script", optional = false ) final String script,
            @Description( "JSON Map of additional parameters for script variables" ) @Parameter( name = "params", optional = true ) final Map params )
    {
        try
        {
            engineReplacementDecision.beforeExecution( script );

            final Bindings bindings = createBindings( neo4j, params );
        
            final Object result = engine(neo4j).eval( script, bindings );
            return ObjectToRepresentationConverter.convert( result );
        }
        catch ( final ScriptException e )
        {
            return ValueRepresentation.string( e.getMessage() );
        }
    }

    private Bindings createBindings( GraphDatabaseService neo4j, Map params )
    {
        final Bindings bindings = createInitialBinding( neo4j );
        if ( params != null )
        {
            bindings.putAll( params );
        }
        return bindings;
    }

    private Bindings createInitialBinding( GraphDatabaseService neo4j )
    {
        final Bindings bindings = new SimpleBindings();
        // TODO: consider moving initial bindings to engine scope
        final Neo4jGraph graph = new Neo4jGraph( neo4j, false );
    	final GremlinPipeline pipeline = new GremlinPipeline();
        bindings.put( g, graph );
        bindings.put( pipe, pipeline );
        bindings.put( gdb, neo4j );
        return bindings;
    }

    private ScriptEngine engine( GraphDatabaseService neo4j )
    {
        if ( this.engine == null
             || engineReplacementDecision.mustReplaceEngine() )
        {
            this.engine = createQueryEngine(neo4j);
         
        }
        return this.engine;
    }

    public Representation getRepresentation( final Object data )
    {
        return ObjectToRepresentationConverter.convert( data );
    }

}
