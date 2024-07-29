package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import microbat.tracerecov.autoprompt.AutoPromptEngineer;
import microbat.util.JavaUtil;

public class AutoPromptEngineeringHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		JavaUtil.sourceFile2CUMap.clear();

		AutoPromptEngineer autoPromptEngineer = new AutoPromptEngineer();
		double originalAvgLoss = autoPromptEngineer.getAverageLoss();
		String newExample = autoPromptEngineer.adjustVariableExpansionPromptExample();
		double updatedAvgLoss = autoPromptEngineer.getAverageLoss(newExample);

		System.out.println("Original Average Loss: " + originalAvgLoss);
		System.out.println("Updated Average Loss: " + updatedAvgLoss);

		return null;
	}

}
