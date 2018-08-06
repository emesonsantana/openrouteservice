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

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import heigit.ors.routing.graphhopper.extensions.storages.GraphStorageUtils;
import heigit.ors.routing.graphhopper.extensions.storages.HeavyVehicleAttributesGraphStorage;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Only certain nodes are accepted and therefor the others are ignored.
 * <p>
 *
 * @author Peter Karich
 */
public class CoreDijkstraFilter implements EdgeFilter {
    private final CHGraph graph;
    private final int maxNodes;
    private final int coreNodeLevel;
    static int count = 0;
    static int countCore = 0;
    static HashSet<Integer> coreNodes = new HashSet<>();
    static HashSet<Integer> nonCoreNodes = new HashSet<>();

    private HeavyVehicleAttributesGraphStorage _storage;
    private byte[] _buffer;


    /**
     *
     * @param g
     */
    public CoreDijkstraFilter(CHGraph g, GraphStorage graphStorage) {
        graph = g;
        maxNodes = g.getNodes();
        coreNodeLevel = maxNodes + 1;
        _buffer = new byte[3];
        _storage = GraphStorageUtils.getGraphExtension(graphStorage, HeavyVehicleAttributesGraphStorage.class);
    }

    /**
     *
     * @param edgeIterState iterator pointing to a given edge
     * @return true iff the edge is virtual or is a shortcut or the level of the base node is greater/equal than
     * the level of the adjacent node
     */
    @Override

    public boolean accept(EdgeIteratorState edgeIterState) {
        int base = edgeIterState.getBaseNode();
        int adj = edgeIterState.getAdjNode();
        // always accept virtual edges, see #288
        if (base >= maxNodes || adj >= maxNodes)
            return true;
//        for (int i = 0; i < graph.getNodes(); i++){
//            if (graph.getLevel(i) == coreNodeLevel) coreNodes.add(i);
//            else nonCoreNodes.add(i);
//    }
//        System.out.println("Non-core size: " + nonCoreNodes.size() + " core size: " + coreNodes.size());

//        System.out.print("basenode " + base + " -> " + adj);
        if(graph.getLevel(base) == coreNodeLevel)         countCore++;
        else count++;
        //System.out.println("(CORE level)");
//        if(((CHEdgeIteratorState) edgeIterState).isShortcut()) System.out.println("(shortcut)");
//        else System.out.println("");
        // minor performance improvement: shortcuts in wrong direction are disconnected, so no need to exclude them
//        if (((CHEdgeIteratorState) edgeIterState).isShortcut())
//            return true;
        if(graph.getLevel(base) == coreNodeLevel) {
            if(((CHEdgeIteratorState) edgeIterState).isShortcut()) return true;
            return !_storage.hasEdgeRestriction(edgeIterState.getOriginalEdge(), _buffer);


        }

//        System.out.println("Visited edges NOT IN core: " + count + "   Visited edges IN core " + countCore);
            return graph.getLevel(base) <= graph.getLevel(adj);
    }
}