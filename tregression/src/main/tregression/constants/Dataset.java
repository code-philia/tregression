package tregression.constants;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum Dataset {
	DEFECTS4J("Defects4J"),
	REGS4J("Regs4J");
	
	private final String name;
    private static final Map<String,Dataset> LOOKUP = new HashMap<>();

    static {
		for(Dataset dataset : EnumSet.allOf(Dataset.class)) {
			LOOKUP.put(dataset.getName(), dataset);
		}
	}
    
	private Dataset(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public static Dataset getDataset(String name) {
		return LOOKUP.get(name);
	}
}