package tregression.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Implementation of hopcroft-karp from
 * geeksforgeeks
 * https://www.geeksforgeeks.org/hopcroft-karp-algorithm-for-maximum-matching-set-2-implementation/
 */
public class BipGraph {

	private static final int NIL = 0;
	private static final int INF = Integer.MAX_VALUE;
	int m, n;
	List<Integer>[] adj;
	int[] pairU, pairV, dist;

	public int hopcroftKarp() {
		pairU = new int[m + 1];
		pairV = new int[n + 1];
		dist = new int[m + 1];
		Arrays.fill(pairU, NIL);
		Arrays.fill(pairV, NIL);
		int result = 0;
		while (bfs()) {

			for (int u = 1; u <= m; u++)

				if (pairU[u] == NIL && dfs(u))
					result++;
		}
		return result;
	}

	boolean bfs() {

		Queue<Integer> Q = new LinkedList<>();

		for (int u = 1; u <= m; u++) {
			if (pairU[u] == NIL) {

				dist[u] = 0;
				Q.add(u);
			}

			else
				dist[u] = INF;
		}
		dist[NIL] = INF;

		while (!Q.isEmpty()) {
			int u = Q.poll();
			if (dist[u] < dist[NIL]) {
				for (int i : adj[u]) {
					int v = i;
					if (dist[pairV[v]] == INF) {
						dist[pairV[v]] = dist[u] + 1;
						Q.add(pairV[v]);
					}
				}
			}
		}
		return (dist[NIL] != INF);
	}

	boolean dfs(int u) {
		if (u != NIL) {
			for (int i : adj[u]) {
				int v = i;
				if (dist[pairV[v]] == dist[u] + 1) {
					if (dfs(pairV[v]) == true) {
						pairV[v] = u;
						pairU[u] = v;
						return true;
					}
				}
			}
			dist[u] = INF;
			return false;
		}
		return true;
	}

	// Constructor
	@SuppressWarnings("unchecked")
	public BipGraph(int m, int n) {
		this.m = m;
		this.n = n;
		adj = new ArrayList[m + 1];
		Arrays.fill(adj, new ArrayList<>());
	}

	public void addEdge(int u, int v) {
		adj[u].add(v);
	}
}
