package tregression.util;

import java.util.List;
import java.util.Map;

import microbat.model.trace.Trace;

public interface TraceMatcher {
	public Map<Long, Long> matchTraces(List<Trace> trace1, List<Trace> trace2);
}
