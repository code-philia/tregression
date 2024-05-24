package tregression.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CodeExtractor {
    public static void main(String[] args) {
        String filePath = "D:\\defects4j\\Math\\5\\bug\\src\\main\\java\\org\\apache\\commons\\math3\\complex\\Complex.java"; // 指定Java文件路径
        int targetLineNumber = 100; // 要提取的行号

        try {
            String[] codeAndContext = getCodeWithContext(filePath, targetLineNumber, 3); // 获取目标行及其上下文5行
            for (String line : codeAndContext) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
}
