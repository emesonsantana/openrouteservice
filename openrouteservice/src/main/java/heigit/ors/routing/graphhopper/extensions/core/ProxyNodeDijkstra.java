/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.routing.graphhopper.extensions.core;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.CHGraphImpl;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Parameters;

import java.util.PriorityQueue;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 * <p>
 *
 * @author Peter Karich
 */
public class ProxyNodeDijkstra extends AbstractRoutingAlgorithm {
    protected IntObjectMap<SPTEntry> fromMap;
    protected PriorityQueue<SPTEntry> fromHeap;
    protected SPTEntry currEdge;
    private int visitedNodes;
    private int maxVisitedNodes = 500;
    private int proxyNode = -1;
    private int to = -1;
    private int coreNodeLevel = -1;
    private CHGraph chGraph;
    EdgeExplorer explorer;

    // Modification by Maxim Rylov: Added a new class variable used for computing isochrones.
    protected Boolean reverseDirection = false;

    public ProxyNodeDijkstra(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode, -1);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        chGraph  = (CHGraph) ((QueryGraph) graph).getMainGraph();
        coreNodeLevel = chGraph.getNodes() + 1;
        initCollections(size);
    }

    public ProxyNodeDijkstra(Graph graph, Weighting weighting, TraversalMode tMode, int maxVisitedNodes) {
        this(graph,weighting,tMode);
        this.maxVisitedNodes = maxVisitedNodes;
    }


    protected void initCollections(int size) {
        fromHeap = new PriorityQueue<SPTEntry>(size);
        fromMap = new GHIntObjectHashMap<SPTEntry>(size);
    }

    @Override
    public Path calcPath(int from, int to) {
        throw new IllegalStateException("Cannot calc a path with this algorithm");

    }

    public int getProxyNode(int from, boolean bwd){
        checkAlreadyRun();
        currEdge = createSPTEntry(from, 0);
        if (!traversalMode.isEdgeBased()) {
            fromMap.put(from, currEdge);
        }
        explorer = bwd? inEdgeExplorer : outEdgeExplorer;
        runAlgo();
        return proxyNode;
    }

    protected void runAlgo() {
        while (true) {
            if(visitedNodes >= maxVisitedNodes) break;
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int startNode = currEdge.adjNode;
            EdgeIterator iter = explorer.setBaseNode(startNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge))
                    continue;

                if(chGraph.getLevel(iter.getAdjNode()) ==  coreNodeLevel){
                    proxyNode = iter.getAdjNode();
                    break;
                }

                int traversalId = traversalMode.createTraversalId(iter, false);
                // Modification by Maxim Rylov: use originalEdge as the previousEdgeId
                double tmpWeight = weighting.calcWeight(iter, reverseDirection, currEdge.originalEdge) + currEdge.weight;
                if (Double.isInfinite(tmpWeight))
                    continue;

                SPTEntry nEdge = fromMap.get(traversalId);
                if (nEdge == null) {
                    nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight);
                    nEdge.parent = currEdge;
                    // Modification by Maxim Rylov: Assign the original edge id.
                    nEdge.originalEdge = iter.getOriginalEdge();   
                    fromMap.put(traversalId, nEdge);
                    fromHeap.add(nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    fromHeap.remove(nEdge);
                    nEdge.edge = iter.getEdge();
                    nEdge.originalEdge = iter.getOriginalEdge();
                    nEdge.weight = tmpWeight;
                    nEdge.parent = currEdge;
                    fromHeap.add(nEdge);
                } else
                    continue;

            }

            if (fromHeap.isEmpty())
                break;

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }
    }

    @Override
    protected boolean finished() {
        return chGraph.getLevel(currEdge.adjNode) == coreNodeLevel;
    }

    @Override
    protected Path extractPath() {
        if (currEdge == null || !finished())
            return createEmptyPath();

        return new Path(graph, weighting, maxSpeed).setWeight(currEdge.weight).setSPTEntry(currEdge).extract();
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.DIJKSTRA;
    }
}