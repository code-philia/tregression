package tregression.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import microbat.model.BreakPoint;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.model.variable.Variable;
import microbat.model.variable.VirtualVar;
import tregression.empiricalstudy.EmpiricalTrial;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.empiricalstudy.RootCauseNode;
import tregression.model.StepOperationTuple;

public class DebuggingTraceRecorder {
	private final int CONTEXT_NUM = 3;
	
	public int condition_result_num = 0;
	
	
	/*
	 * functions to collect information
	 */
	
	public void recordDebuggingTrace(List<EmpiricalTrial> trials, String projectName, int bugID,
			String outputFilePath) {
		for(int i=0; i<trials.size(); i++) {
			System.out.println("--INFO-- Recording trial " + (i+1));
			System.out.println(trials.get(i));


			EmpiricalTrial t = trials.get(i);
			
			TraceNode rootCauseNode = t.getRootcauseNode();
			boolean findRootCause = (rootCauseNode!=null);
			
			RootCauseFinder rootCauseFinder = t.getRootCauseFinder();
			List<Integer> rootCauseOrders = new ArrayList<Integer>();
			for(RootCauseNode rcNode: rootCauseFinder.getRealRootCaseList()) {
				if(rcNode.isOnBefore()) {
					rootCauseOrders.add(rcNode.getRoot().getOrder());
				}
			}

			if(t.getCheckList()==null || t.getCheckList().size()==0 || t.getCheckList().size()>30) {
				continue;
			}
	        try (FileWriter writer = new FileWriter(outputFilePath, true)) {
	            writer.write("[Debugging trace START]\n");
	            writer.write("Project:"+projectName+"\n");
	            writer.write("BugID:"+bugID+"\n");
	            for(StepOperationTuple step: t.getCheckList()) {
	            	TraceNode node = step.getNode();
	            	if(node==null) {
	            		continue;
	            	}

	            	condition_result_num = 0;

	            	StringBuffer buffer = new StringBuffer();
	            	buffer.append("<step START>\n");
	            	buffer.append("order:"+node.getOrder()+"\n");
	            	//step type
	            	String stepType = step.getUserFeedback().getFeedbackType();
	            	if(rootCauseOrders.contains(node.getOrder())) {
	            		buffer.append("type:root cause\n");
	            	}
	            	else {
	            		buffer.append("type:"+stepType+"\n");
	            	}

	            	//context
	            	BreakPoint breakPoint = node.getBreakPoint();
	            	int lineNumber = breakPoint.getLineNumber();
	            	String filePath = breakPoint.getFullJavaFilePath();
	            	String doc = getClassFunName(filePath,lineNumber);
	            	String[] code_window = getCodeWithContext(filePath,lineNumber,CONTEXT_NUM);
	            	String preText = "";
	            	String postText = "";
	            	for(int bias = 1;bias<=CONTEXT_NUM;bias++) {
	            		preText = code_window[CONTEXT_NUM-bias]+preText;
	            		postText = postText+code_window[CONTEXT_NUM+bias];
	            	}
	            	
	            	int firstIndex = doc.indexOf("#");
	            	int secondIndex = doc.indexOf("#", firstIndex + 1);
	            	
	            	buffer.append("classFuncName:"+doc.substring(0, secondIndex)+"\n");
	            	buffer.append("comment:"+doc.substring(secondIndex + 1)+"\n");
	            	buffer.append("lineNumber:"+lineNumber+"\n");
	            	buffer.append("target:"+code_window[CONTEXT_NUM]+"\n");
	            	buffer.append("preText:"+preText+"\n");
	            	buffer.append("postText:"+postText+"\n");
	            	
	            	//all variables
	            	List<VarValue> readVariables = node.getReadVariables();
	            	List<VarValue> writtenVariables = node.getWrittenVariables();
	            	
	            	//wrong variable (if has)
	            	List<String> wrongVarIDs = null;
	            	if(step.getUserFeedback().getOption()!=null) {
	            		wrongVarIDs = step.getUserFeedback().getOption().getIncludedWrongVarID();
	            	}
	            	else {
	            		wrongVarIDs = new ArrayList<String>();
	            	}

	            	buffer.append("<readVarValues start>\n");
	            	for(VarValue var_value : readVariables) {
	            		Variable variable = var_value.getVariable();
	            		buffer.append("Type:"+getClassName(variable.getType())+"\n");
	            		buffer.append("Name:"+parseVarName(variable,readVariables)+"\n");
	            		buffer.append("Value:"+getObjValue(var_value)+"\n");
	            		buffer.append("Correct:"+!(wrongVarIDs.contains(variable.getVarID()))+"\n");
	            		buffer.append("isField:"+var_value.isField()+"\n");
	            		buffer.append("isLocal:"+var_value.isLocalVariable()+"\n");
	            		buffer.append("isStatic:"+var_value.isStatic()+"\n");
	            		buffer.append("================\n");
	            	}
	            	buffer.append("<readVarValues end>\n");
	            	buffer.append("<writtenVarValues start>\n");
	            	for(VarValue var_value : writtenVariables) {
	            		Variable variable = var_value.getVariable();
	            		buffer.append("Type:"+getClassName(variable.getType())+"\n");
	            		buffer.append("Name:"+parseVarName(variable,readVariables)+"\n");
	            		buffer.append("Value:"+getObjValue(var_value)+"\n");
	            		buffer.append("Correct:"+!(wrongVarIDs.contains(variable.getVarID()))+"\n");
	            		buffer.append("isField:"+var_value.isField()+"\n");
	            		buffer.append("isLocal:"+var_value.isLocalVariable()+"\n");
	            		buffer.append("isStatic:"+var_value.isStatic()+"\n");
	            		buffer.append("================\n");
	            	}
	            	buffer.append("<writtenVarValues end>\n");
	            	buffer.append("<step END>\n\n");
	        		writer.write(buffer.toString());
	            }

	            writer.write("[Debugging trace END]\n\n");
	            
	            break;
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
		}		
	}
	
	
	// given line number, get target code
    public String getLineFromFile(String filePath, int lineNumber) throws IOException {
    	if(lineNumber<=0) {
    		return "";
    	}
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int currentLine = 1;

            while ((line = reader.readLine()) != null && currentLine < lineNumber) {
                currentLine++;
            }
            if (lineNumber == currentLine) {
            	if(line == null) {
            		return "";
            	}
                return line.strip();
            } else {
                return "";
            }
        }
    }
    
    // Get source code with context, codeWithContext[contextLines] = target
    public static String[] getCodeWithContext(String filePath, int targetLineNumber, int contextLines) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String[] codeWithContext = new String[2 * contextLines + 1];
        
        int prevStart = targetLineNumber - contextLines;
        int startLine = Math.max(1, prevStart);
        int endLine = targetLineNumber + contextLines;
        int index = 0;
        if(prevStart < 1) {
        	while(index<1-prevStart) {
        		codeWithContext[index++] = "";
        	}
        }
        
        // 读取文件，直到达到目标行号或文件结束
        int currentLineNumber = 1;
        while (currentLineNumber < startLine) {
            String line = reader.readLine();
            if (line == null) {
                break; // 文件结束
            }
            currentLineNumber++;
        }

        // 读取目标行及其上下文
        for (int i = startLine; i <= endLine; i++) {
            String line = reader.readLine();
            if (line == null) {
                break; // 文件结束
            }
            else if("".equals(line)) {
            	codeWithContext[index++] = "";
            }
            else {
//                int numSpaces = line.length() - line.trim().length();
//                String tabs = "\\t".repeat(numSpaces / 4);
                codeWithContext[index++] = line.trim()+"\\n ";
            }
        }
        
        while(index < 2 * contextLines + 1) {
        	codeWithContext[index++] = "";
        }

        reader.close();
        return codeWithContext;
    }
    
   
    public String parseVarName(Variable variable, List<VarValue> readVarValues) {
    	String varName = variable.getName();
    	if(variable instanceof VirtualVar) {
    		String result = "return_from_";
    		String[] parts = varName.split("#");

    		String className = parts[0];
    		result+=(getClassName(className)+".");

    		String methodName = parts[1].split( "\\(" )[0];
    		result+=methodName;
    		return result;
    	}
    	//ConditionResult_36916
    	else if(varName.contains("ConditionResult_")) {
    		String result =  "condition_result_"+String.valueOf(condition_result_num);
    		condition_result_num+=1;
    		return result;
    	}
    	else if(varName.contains("[")) {
    		int index = varName.indexOf("[");
    		String address = varName.substring(0,index);
    		varName = varName.substring(index);
    		
    		for(VarValue v : readVarValues) {
    			String oriValue = v.getStringValue();
    			if(oriValue.contains("@")) {
    				String afterAt = oriValue.substring(oriValue.indexOf("@")+1);
    				if(Long.parseLong(afterAt,16) == Long.parseLong(address,10)) {
    					varName = (v.getVarName()+varName);
    					break;
    				}
    			}
    		}
    		
    	}
    	return varName;
    }

    // given full name, get class name
    public String getClassName(String classFullName) {
    	if(!classFullName.contains(".")) {
    		return classFullName;
    	}
    	String[] parts = classFullName.split("\\.");
    	return parts[parts.length-1];
    }
    
    // given varValue, get string value
    public String getObjValue(VarValue var_value) {
    	Variable variable = var_value.getVariable();
    	String type = variable.getType();
    	// basic type
    	if("byte".equals(type)||"short".equals(type)||"int".equals(type)||"long".equals(type)
    			||"float".equals(type)||"double".equals(type)||"char".equals(type)||"boolean".equals(type)) {
    		return var_value.getStringValue();
    	}
    	// java Array
    	else if(var_value.isArray()) {
    		List<VarValue> children = var_value.getChildren();
    		int len = Math.min(children.size(), 10);
        	String valueString = "[";
        	for(int i = 0;i<len;i++) {
        		VarValue child = children.get(i);
        		valueString += getObjValue(child);
        		if(i<len-1) {
        			valueString+=",";
        		}
        	}
        	if(len < children.size()) {
        		valueString += "...]";
        	}
        	else {
        		valueString += "]";
        	}
        	return valueString;    	
        }
    	// has toString method
    	else if(var_value.isDefinedToStringMethod()) {
    		return var_value.getStringValue();
    	}
    	// object with no toString method, record its fields
    	else { 
        	List<VarValue> children = var_value.getChildren();
        	int len = Math.min(children.size(),10);
        	String valueString = "{";
        	for(int i = 0;i<len;i++) {
        		VarValue child = children.get(i);
        		valueString+=(child.getVariable().getName()+":"+getObjValue(child));
        		if(i<len-1) {
        			valueString+=",";
        		}
        	}
        	if(len < children.size()) {
        		valueString += "...}";
        	}
        	else {
        		valueString += "}";
        	}
        	return valueString;
    	}
    }

    // get class, function name and comment
    public String getClassFunName(String filename, int lineNumber) {
    	FileInputStream in = null;
		try {
			in = new FileInputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(in);
        Optional<CompilationUnit> optionalCu = parseResult.getResult();

        String className = "";
        String result = "";
        if (optionalCu.isPresent()) {
            CompilationUnit cu = optionalCu.get();
            for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                className = cls.getNameAsString();
                for (MethodDeclaration method : cls.findAll(MethodDeclaration.class)) {
                    int startLine = method.getBegin().get().line;
                    int endLine = method.getEnd().get().line;

                    if (lineNumber >= startLine && lineNumber <= endLine) {
                        result = className+"#"+method.getNameAsString();
                        
                        Optional<String> comment = method.getComment().map(c->c.getContent());
                        if(comment.isPresent()) {
                        	String commentStr = comment.get();
                        	commentStr = commentStr.replaceAll("\r|\n|\t"," ");
                        	commentStr = commentStr.replaceAll("( |\t)+"," ");
                        	result+=("#"+commentStr);   
                        }
                        else {
                        	result+=("#");
                        }
                    }
                }
            }
        }
        if("".equals(result)) {
        	return className+"##";
        }
        else {
        	return result;
        }
    }
}
