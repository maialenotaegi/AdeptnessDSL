package org.xtext.example.mydsl.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.script.ScriptException;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.EValidatorRegistrar;
import org.xtext.example.mydsl.adeptness.AbstractElement2;
import org.xtext.example.mydsl.adeptness.AdeptnessPackage;
import org.xtext.example.mydsl.adeptness.At_least;
import org.xtext.example.mydsl.adeptness.At_most;
import org.xtext.example.mydsl.adeptness.Bound_Down;
import org.xtext.example.mydsl.adeptness.Bound_up;
import org.xtext.example.mydsl.adeptness.Checks;
import org.xtext.example.mydsl.adeptness.DOUBLE;
import org.xtext.example.mydsl.adeptness.Exactly;
import org.xtext.example.mydsl.adeptness.ExpressionsModel;
import org.xtext.example.mydsl.adeptness.FailReason;
import org.xtext.example.mydsl.adeptness.Gap;
import org.xtext.example.mydsl.adeptness.HighPeak;
import org.xtext.example.mydsl.adeptness.HighTime;
import org.xtext.example.mydsl.adeptness.Library;
import org.xtext.example.mydsl.adeptness.Lower;
import org.xtext.example.mydsl.adeptness.NotSame;
import org.xtext.example.mydsl.adeptness.Operators;
import org.xtext.example.mydsl.adeptness.Oracle;
import org.xtext.example.mydsl.adeptness.Range;
import org.xtext.example.mydsl.adeptness.Reason;
import org.xtext.example.mydsl.adeptness.Same;
import org.xtext.example.mydsl.adeptness.Signal;
import org.xtext.example.mydsl.adeptness.TimeType;
import org.xtext.example.mydsl.adeptness.Upper;
import org.xtext.example.mydsl.adeptness.Wait;
import org.xtext.example.mydsl.adeptness.When;
import org.xtext.example.mydsl.adeptness.While;

import com.oracle.truffle.js.scriptengine.GraalJSEngineFactory;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

public class OracleAssesment extends AbstractAdeptnessValidator {

	MonitoringVariables monitoringVariables;

	@Check
	public void init(Signal CPS) {
		monitoringVariables = MonitoringVariables.getInstance(CPS.getName());
	}

	private Constants.PRECONDS precond;
	private Constants.PATTERNS pattern;
	private Constants.TIMING_PATTERNS tpattern;
	private Set<Constants.FAILREASONS> failReasons = new HashSet<Constants.FAILREASONS>();

	@Check
	public void resetOracle(Oracle oracle) {
		resetParameters();
	}

	@Check
	public void checkEmptyOracleName(Oracle or) {
		if (or.getName() == null) {
			error("Oracles must be given a name", AdeptnessPackage.Literals.ORACLE__NAME);
		}
	}

	// DETECT ERRORS WITHIN EXPRESSIONS
	@Check
	public void checkExpressionModelVarsInMonitoringVariablesFile(AbstractElement2 elem) {
		if (elem.getUncer1() != null || elem.getUncer2() != null || elem.getUncer3() != null) {
			// TODO: check with operational data when uncertainty type checks is set
			return;
		}
		if (elem.getName() == null)
			return;
		checkNameInMonitoringVariablesFile(elem.getName(), AdeptnessPackage.Literals.ABSTRACT_ELEMENT2__NAME);
	}

	@Check
	public void checkCheckNameInMonitoringVariablesFile(Checks check) {
		if (check.getName() == null)
			return;
		checkNameInMonitoringVariablesFile(check.getName(), AdeptnessPackage.Literals.CHECKS__NAME);
	}

	private void checkNameInMonitoringVariablesFile(String name, EAttribute reference) {
		MonitoringVar emVar = monitoringVariables.getVariables().get(name);
		if (emVar == null) {
			error("Variable " + name + " is not in the monitoring plan", reference);
		}
	}

	@Check
	public void checkMathLibrary(ExpressionsModel exp) {
		boolean mathFound = false;
		boolean signalFound = false;
		String library = "";
		int frontParenthesis = 0;
		int backParenthesis = 0;
		// error if comparison or logical operator
		for (int i = 0; i < exp.getElements().size(); i++) {
			AbstractElement2 el = exp.getElements().get(i);
			if (el.getMath() != null) {
				// Math libraries cannot be used recursively
				if (mathFound) {
					error("Mathematical functions cannot be used recursively.",
							AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
					return;
				}
				mathFound = true;
				library = getLibrary(el.getMath().getLibrary());
				if (library.equals("unknown")) {
					error("Unknown mathematical library.", AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
					return;
				}
				// Math must be followed by a front parenthesis
				if (exp.getElements().get(i + 1) == null
						|| exp.getElements().get(i + 1).getFrontParentheses().size() == 0) {
					error("The expression to apply " + library + " must be surrounded by parenthesis.",
							AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
					return;
				}
			}

			if (mathFound) {
				frontParenthesis += el.getFrontParentheses().size();
				if (el.getName() != null) {
					signalFound = true;
				}
				if (el.getOp() != null) {
					for (int j = 0; j < el.getOp().size(); j++) {
						if (mathFound && el.getOp().get(j).getComparation() != null) {
							error("Mathematical functions does not allow comparison operators.",
									AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
							return;
						}
						if (mathFound && el.getOp().get(j).getLogicOperator() != null) {
							error("Mathematical functions does not allow logical operators.",
									AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
							return;
						}
						if (mathFound && el.getOp().get(j).getBackParentheses() != null) {
							backParenthesis++;
							if (frontParenthesis == backParenthesis) {
								if (!signalFound) {
									error("Mathematical functions must be applied to at least one signal.",
											AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
									return;
								}
								mathFound = false;
								frontParenthesis = 0;
								backParenthesis = 0;
								signalFound = false;
							}
						}
					}
				}
			}

		}
		if (mathFound && frontParenthesis != backParenthesis) {
			error("The expression to apply " + library + " must be surrounded by parenthesis.",
					AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
			return;
		}
	}

	private String getLibrary(Library lib) {
		if (lib == null) {
			return "unknown";
		} else if (lib.getCos() != null) {
			return "cosine";
		} else if (lib.getSin() != null) {
			return "sine";
		} else if (lib.getDerivative() != null) {
			return "devirative";
		} else if (lib.getModulus() != null) {
			return "modulus";
		}
		return "unknown";
	}

	@Check
	public void checkExpressions(ExpressionsModel data) {
		int conOpenPar = 0, conClosePar = 0;
		boolean uncer = false;

		for (int i = 0; i < data.getElements().size(); i++) {
			AbstractElement2 elements = data.getElements().get(i);

			if (elements.getUncer1() != null || elements.getUncer2() != null || elements.getUncer3() != null) {
				uncer = true;
			}

			conOpenPar = conOpenPar + elements.getFrontParentheses().size();

			// current element (which is not the first one) contains name or value,
			// and preceding element also contains name or value.
			if (i > 0 && (elements.getName() != null || elements.getValue() != null)
					&& (data.getElements().get(i - 1).getName() != null
							|| data.getElements().get(i - 1).getValue() != null)) {
				// preceding element does not contain operation.
				// Example: "var 1" or "var ( var"
				if (data.getElements().get(i - 1).getOp().size() == 0) {
					error("Two values or signals cannot be concatenated without an operator",
							AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
				}
			}

			for (int j = 0; j < elements.getOp().size(); j++) {
				if (elements.getOp().get(j).getBackParentheses() != null) {
					conClosePar++;
				}

				// detect <) or &&) like errors
				if (j > 0 && elements.getOp().get(j).getBackParentheses() != null
						&& elements.getOp().get(j - 1).getBackParentheses() == null) {
					error("Operators cannot be concatenated this way",
							AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
				}
				// detect + && like errors
				if (j > 0 && elements.getOp().get(j).getBackParentheses() == null
						&& elements.getOp().get(j - 1).getBackParentheses() == null) {
					error("Operators cannot be concatenated this way",
							AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
				}
			}
		}
		if (conOpenPar != conClosePar) {
			error("Parentheses are not correctly opened and closed",
					AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
		}

		// Check if expression model is correctly evaluated otherwise is not well
		// formated.
		if (!uncer && evalExpression(getExpression("basic", data.getElements())) == null) {
			error("Incorrect expression.", AdeptnessPackage.Literals.EXPRESSIONS_MODEL__ELEMENTS);
		}
	}

	// PRECONDITION CHECKS AND INFORMATION GATHERING
	@Check
	public void checkWhileConditions(While whi) {
		this.precond = Constants.PRECONDS.WHILE;
		checkComparison(whi.getEm(), AdeptnessPackage.Literals.WHILE__EM);
	}

	@Check
	public void checkWhenConditions(When whe) {
		if (whe.getAw() != null) {
			this.precond = Constants.PRECONDS.WHENAFTERWHEN;
		} else {
			this.precond = Constants.PRECONDS.WHEN;
		}
		checkComparison(whe.getEm(), AdeptnessPackage.Literals.WHEN__EM);
	}

	private void checkComparison(ExpressionsModel expr, EReference reference) {
		if (expr == null) {
			error("Condition cannot be empty.", reference);
			return;
		}

		int contLogicOp = 0, contCompOp = 0;
		boolean mathFound = false;
		int frontParenthesis = 0, backParenthesis = 0;
		for (int i = 0; i < expr.getElements().size(); i++) {
			AbstractElement2 element = expr.getElements().get(i);
			if (element.getMath() != null) {
				mathFound = true;
			}
			if (mathFound) {
				frontParenthesis += element.getFrontParentheses().size();
			}
			for (int j = 0; j < element.getOp().size(); j++) {
				if (mathFound && element.getOp().get(j).getBackParentheses() != null) {
					backParenthesis++;
					if (frontParenthesis == backParenthesis) {
						mathFound = false;
						frontParenthesis = 0;
						backParenthesis = 0;
					}
				}
				if (!mathFound && element.getOp().get(j).getLogicOperator() != null) {
					contLogicOp++;
				} else if (!mathFound && element.getOp().get(j).getComparation() != null) {
					contCompOp++;
				}
			}
		}
		if (contLogicOp + 1 < contCompOp) {
			error("Conditions must be concatenated by logical operators.", reference);
			return;
		} else if (contLogicOp + 1 != contCompOp) {
			error("Expression must be a condition.", reference);
			return;
		}
	}

	@Check
	public void checkAfterWhenEmptyValues(Wait wait) {
		if (wait.getUnit() == null) {
			error("Enter a time unit (milliseconds, seconds, minutes or hours).", AdeptnessPackage.Literals.WAIT__UNIT);
			return;
		}

		// there is no way to detect empty wait.getTime() -> takes 0.0 value by default
		// automatic validation detects it anyway -> EDOUBLE cannot be null
		double dur = wait.getTime().getDVal();
		if (dur < 0) {
			error("Duration must be positive.", AdeptnessPackage.Literals.WAIT__TIME);
			return;
		}
	}

	// CHECKS STATEMENT CHECKS AND INFORMATION GATHERING
	@Check
	public void checkCheckSignal(Checks check) {
		// Left part in check must contain at least a variable name
		if (check.getName() == null && check.getEm() == null) {
			error("Checks' left part must represent a signal, cannot be empty.", AdeptnessPackage.Literals.CHECKS__EM);
			return;
		}

		List<AbstractElement2> elems = check.getEm().getElements();
		boolean anyVar = false;
		for (AbstractElement2 elem : elems) {
			if (elem.getName() != null || elem.getUncer1() != null || elem.getUncer2() != null
					|| elem.getUncer3() != null) {
				anyVar = true;
				break;
			}
		}
		if (!anyVar) {
			error("Checks' left part must represent a signal, cannot be a value.",
					AdeptnessPackage.Literals.CHECKS__EM);
			return;
		}

	}

	@Check
	public void checkReferenceBetweenMonitoringVariableMinMax(Checks check) {
		if (check.getEm() != null) {
//			System.out.println(
//					"TODO Check if expressionsModel within Checks statement is correct according to min, max variable values in monitoring plan");
			return;
		}

		MonitoringVar checkVar = monitoringVariables.getVariables().get(check.getName().toString());
		if (checkVar == null)
			return;

		double max = checkVar.getMax();
		double min = checkVar.getMin();

		Bound_up bound_up = null;
		Bound_Down bound_down = null;
		if (check.getReference().getUpper() != null) {
			bound_up = check.getReference().getUpper().getBound_upp();
		} else if (check.getReference().getLower() != null) {
			bound_down = check.getReference().getLower().getBound_lower();
		} else if (check.getReference().getRange() != null) {
			bound_down = check.getReference().getRange().getBound_lower();
			bound_up = check.getReference().getRange().getBound_upp();
		} else if (check.getReference().getGap() != null) {
			bound_down = check.getReference().getGap().getBound_lower();
			bound_up = check.getReference().getGap().getBound_upp();
		} else if (check.getReference().getSame() != null) {
			bound_up = check.getReference().getSame().getBound_upp();
		} else if (check.getReference().getNotsame() != null) {
			bound_up = check.getReference().getNotsame().getBound_upp();
		}

		Double boundup = null, boundown = null;
		if (bound_up != null) {
			if (bound_up.getEm() != null) {
//				System.out.println(
//						"TODO Check if expressionsModel within Upper bound statement is correct according to min, max variable values in monitoring plan");
			}
			boundup = bound_up.getValue().getDVal();
			if (boundup > max) {
				error("Check " + check.getName() + " with value: " + boundup + " does not comply max value: " + max
						+ " specified in the validation plan", AdeptnessPackage.Literals.CHECKS__REFERENCE);
			}
			if (boundup < min) {
				error("Check " + check.getName() + " with value: " + boundup + " does not comply min value: " + min
						+ " specified in the validation plan", AdeptnessPackage.Literals.CHECKS__REFERENCE);
			}
		}
		if (bound_down != null) {
			if (bound_down.getEm() != null) {
//				System.out.println(
//						"TODO Check if expressionsModel within Lower bound statement is correct according to min, max variable values in monitoring plan");
			}
			boundown = bound_down.getValue().getDVal();
			if (boundown > max) {
				error("Check " + check.getName() + " with value: " + boundown + " does not comply max value: " + max
						+ " specified in the validation plan", AdeptnessPackage.Literals.CHECKS__REFERENCE);
			}
			if (boundown < min) {
				error("Check " + check.getName() + " with value: " + boundown + " does not comply min value: " + min
						+ " specified in the validation plan", AdeptnessPackage.Literals.CHECKS__REFERENCE);
			}
		}
	}

	@Check
	public void checkChecksCondition(Checks check) {
		for (int i = 0; i < check.getEm().getElements().size(); i++) {
			AbstractElement2 element = check.getEm().getElements().get(i);
			for (int j = 0; j < element.getOp().size(); j++) {
				if (element.getOp().get(j).getLogicOperator() != null) {
					error("Operator: " + element.getOp().get(j).getLogicOperator().getOp().toString()
							+ " is not available for checks", AdeptnessPackage.Literals.CHECKS__EM);
				} else if (element.getOp().get(j).getComparation() != null) {
					error("Operator: " + element.getOp().get(j).getComparation().getOp().toString()
							+ " is not available for checks", AdeptnessPackage.Literals.CHECKS__EM);
				}
			}
		}
	}

	@Check
	public void checkEmptyLowerValue(Lower lower) {
		this.pattern = Constants.PATTERNS.ABOVE_REFERENCE;
		checkBound(lower.getBound_lower().getValue(), lower.getBound_lower().getEm(), "Lower",
				AdeptnessPackage.Literals.LOWER__BOUND_LOWER);
		checkTempCondWithPrecond(lower.getAtleast() != null, AdeptnessPackage.Literals.LOWER__ATLEAST);
		checkTempCondWithPrecond(lower.getAtmost() != null, AdeptnessPackage.Literals.LOWER__ATMOST);
		checkTempCondWithPrecond(lower.getExactly() != null, AdeptnessPackage.Literals.LOWER__EXACTLY);
	}

	@Check
	public void checkEmptyUpperValue(Upper upper) {
		this.pattern = Constants.PATTERNS.BELOW_REFERENCE;
		checkBound(upper.getBound_upp().getValue(), upper.getBound_upp().getEm(), "Upper",
				AdeptnessPackage.Literals.UPPER__BOUND_UPP);
		checkTempCondWithPrecond(upper.getAtleast() != null, AdeptnessPackage.Literals.UPPER__ATLEAST);
		checkTempCondWithPrecond(upper.getAtmost() != null, AdeptnessPackage.Literals.UPPER__ATMOST);
		checkTempCondWithPrecond(upper.getExactly() != null, AdeptnessPackage.Literals.UPPER__EXACTLY);
	}

	@Check
	public void checkBoundValues(Range range) {
		this.pattern = Constants.PATTERNS.RANGE;
		checkBound(range.getBound_lower().getValue(), range.getBound_lower().getEm(), "Lower",
				AdeptnessPackage.Literals.RANGE__BOUND_LOWER);
		checkBound(range.getBound_upp().getValue(), range.getBound_upp().getEm(), "Upper",
				AdeptnessPackage.Literals.RANGE__BOUND_UPP);
		checkDownBoundLower(range.getBound_lower().getValue(), range.getBound_upp().getValue(),
				AdeptnessPackage.Literals.RANGE__BOUND_LOWER);

		checkTempCondWithPrecond(range.getAtleast() != null, AdeptnessPackage.Literals.RANGE__ATLEAST);
		checkTempCondWithPrecond(range.getAtmost() != null, AdeptnessPackage.Literals.RANGE__ATMOST);
		checkTempCondWithPrecond(range.getExactly() != null, AdeptnessPackage.Literals.RANGE__EXACTLY);

	}

	@Check
	public void checkBoundValues(Gap gap) {
		this.pattern = Constants.PATTERNS.GAP;
		checkBound(gap.getBound_lower().getValue(), gap.getBound_lower().getEm(), "Lower",
				AdeptnessPackage.Literals.GAP__BOUND_LOWER);
		checkBound(gap.getBound_upp().getValue(), gap.getBound_upp().getEm(), "Upper",
				AdeptnessPackage.Literals.GAP__BOUND_UPP);
		checkDownBoundLower(gap.getBound_lower().getValue(), gap.getBound_upp().getValue(),
				AdeptnessPackage.Literals.GAP__BOUND_LOWER);
		checkTempCondWithPrecond(gap.getAtleast() != null, AdeptnessPackage.Literals.GAP__ATLEAST);
		checkTempCondWithPrecond(gap.getAtmost() != null, AdeptnessPackage.Literals.GAP__ATMOST);
		checkTempCondWithPrecond(gap.getExactly() != null, AdeptnessPackage.Literals.GAP__EXACTLY);
	}

	@Check
	public void checkEmptyLowerValue(Same same) {
		this.pattern = Constants.PATTERNS.SAME_REFERENCE;
		checkBound(same.getBound_upp().getValue(), same.getBound_upp().getEm(), "Upper",
				AdeptnessPackage.Literals.SAME__BOUND_UPP);
		checkTempCondWithPrecond(same.getAtleast() != null, AdeptnessPackage.Literals.SAME__ATLEAST);
		checkTempCondWithPrecond(same.getAtmost() != null, AdeptnessPackage.Literals.SAME__ATMOST);
		checkTempCondWithPrecond(same.getExactly() != null, AdeptnessPackage.Literals.SAME__EXACTLY);
	}

	@Check
	public void checkEmptyLowerValue(NotSame notSame) {
		this.pattern = Constants.PATTERNS.NOTSAME_REFERENCE;
		checkBound(notSame.getBound_upp().getValue(), notSame.getBound_upp().getEm(), "Upper",
				AdeptnessPackage.Literals.NOT_SAME__BOUND_UPP);
		checkTempCondWithPrecond(notSame.getAtleast() != null, AdeptnessPackage.Literals.NOT_SAME__ATLEAST);
		checkTempCondWithPrecond(notSame.getAtmost() != null, AdeptnessPackage.Literals.NOT_SAME__ATMOST);
		checkTempCondWithPrecond(notSame.getExactly() != null, AdeptnessPackage.Literals.NOT_SAME__EXACTLY);
	}

	private void checkBound(DOUBLE value, ExpressionsModel em, String type, EReference reference) {
		if (value == null && em == null) {
			error(type + " bound must be a value or an expression", reference);
		}
	}

	private void checkDownBoundLower(DOUBLE lowerBound, DOUBLE upperBound, EReference reference) {
		if (lowerBound != null && upperBound != null) {
			if (lowerBound.getDVal() > upperBound.getDVal()) {
				error("Lower bound cannot be higher than upper bound", reference);
			}
		} else {
//			System.out.println(
//					"TODO: check ExpressionsModel to check if lower bound is really lower than upper bound in a range");
		}
	}

	private void checkTempCondWithPrecond(boolean temp, EReference reference) {
		if (temp && this.precond == null) {
			error("Temporary conditions within assertions should only be used in conjuction with \"while\" or \"when\" preconditions.",
					reference);
		}
	}

	@Check
	public void checkAtLeastTime(At_least atLeast) {
		this.tpattern = Constants.TIMING_PATTERNS.ATLEAST;
		checkTimeAndDuration(atLeast.getTime(), atLeast.getUnit(), AdeptnessPackage.Literals.AT_LEAST__TIME,
				AdeptnessPackage.Literals.AT_LEAST__UNIT);
	}

	@Check
	public void checkAtMostTimeAndDuration(At_most atMost) {
		this.tpattern = Constants.TIMING_PATTERNS.ATMOST;
		checkTimeAndDuration(atMost.getTime(), atMost.getUnit(), AdeptnessPackage.Literals.AT_MOST__TIME,
				AdeptnessPackage.Literals.AT_MOST__UNIT);
	}

	@Check
	public void checkExactlyTime(Exactly exactly) {
		this.tpattern = Constants.TIMING_PATTERNS.EXACTLY;
		checkTimeAndDuration(exactly.getTime(), exactly.getUnit(), AdeptnessPackage.Literals.EXACTLY__TIME,
				AdeptnessPackage.Literals.EXACTLY__UNIT);
	}

	private void checkTimeAndDuration(DOUBLE time, TimeType timeUnit, EReference timeReference,
			EReference unitReference) {
		if (time.getDVal() < 0) {
			error("Duration must be positive.", timeReference);
			return;
		} else if (time.getDVal() == 0) {
			error("Duration cannot be zero.", timeReference);
			return;
		}

		if (timeUnit == null) {
			error("Enter time unit (milliseconds, seconds, minutes or hours).", unitReference);
			return;
		}
	}

	// FAILS IF STATEMENTS CHECKS
	@Check
	public void checkHighTimeAndHighPeak(Checks check) {
		boolean HT = false;
		boolean HP = false;
		int numHT = 0, numHP = 0;
		for (int i = 0; i < check.getFailReason().size(); i++) {
			if (check.getFailReason().get(i).getReason().getHighTime() != null) {
				HT = true;
				numHT = i;
			} else if (check.getFailReason().get(i).getReason().getHighPeak() != null) {
				HP = true;
				numHP = i;
			}
		}
		if (HT && HP) {
			HighTime Ht = check.getFailReason().get(numHT).getReason().getHighTime();
			HighPeak Hp = check.getFailReason().get(numHP).getReason().getHighPeak();
			if (Ht.getCant().getDVal() <= Hp.getCant().getDVal()) {
				error("High peak reference's confidence value must be lower than High time out of bound reference's confidence value",
						AdeptnessPackage.Literals.CHECKS__FAIL_REASON);
			}
		}
	}

	@Check
	public void checkFailReasons(FailReason fr) {
		if (fr.getReason() == null) {
			error("Incomplete fails if statement. ", AdeptnessPackage.Literals.FAIL_REASON__REASON);
		}
	}

	@Check
	public void checkFailReasons(Reason fr) {
		Constants.FAILREASONS frk = null;
		double confidence = 0.0;
		int nSamples = 1, nPeaks = 1;
		EReference reference = null;
		if (fr.getHighPeak() != null) {
			confidence = fr.getHighPeak().getCant().getDVal();
			frk = Constants.FAILREASONS.HIGH_PEAK;
			reference = AdeptnessPackage.Literals.REASON__HIGH_PEAK;
		} else if (fr.getHighTime() != null) {
			confidence = fr.getHighTime().getCant().getDVal();
			nSamples = Utils.getNSamples((int) fr.getHighTime().getTime().getDVal(),
					fr.getHighTime().getUnit().getTime());
			nPeaks = nSamples;
			frk = Constants.FAILREASONS.HIGH_TIME_OUT_BOUNDS;
			reference = AdeptnessPackage.Literals.REASON__HIGH_TIME;
		} else if (fr.getXPeaks() != null) {
			confidence = fr.getXPeaks().getCant().getDVal();
			nPeaks = (int) fr.getXPeaks().getNPeaks().getDVal();
			nSamples = Utils.getNSamples((int) fr.getXPeaks().getTime().getDVal(), fr.getXPeaks().getUnit().getTime());
			frk = Constants.FAILREASONS.X_PEAKS_XSECONDS;
			reference = AdeptnessPackage.Literals.REASON__XPEAKS;
		} else if (fr.getConstDeg() != null) {
			frk = Constants.FAILREASONS.CONSTANT_DEGRADATION;
			reference = AdeptnessPackage.Literals.REASON__CONST_DEG;
		}

		// check structural errors.
		if (nSamples <= 0 || nPeaks <= 0) {
			error("Duration or number of peaks cannot be zero or lower.", reference);
		}
		if (pattern.equals(Constants.PATTERNS.NOTSAME_REFERENCE) && confidence != 0) {
			error("Confidence value must be zero within 'should not be' clauses or use a 'not in range' clause instead.",
					reference);
		}
		if ((frk.equals(Constants.FAILREASONS.HIGH_TIME_OUT_BOUNDS)
				|| frk.equals(Constants.FAILREASONS.X_PEAKS_XSECONDS)) && this.tpattern != null) {
			error("Temporary conditions are either set within the assertion or the failure statement, but not in both.",
					reference);
		}
		if (frk.equals(Constants.FAILREASONS.CONSTANT_DEGRADATION)) {
			if (this.precond == Constants.PRECONDS.WHEN || this.precond == Constants.PRECONDS.WHENAFTERWHEN) {
				error("Constant degradation only allows while preconditions or no preconditions at all.", reference);
			}
			if (this.tpattern != null) {
				error("Constant degradation does not allow temporary conditions.", reference);
			}
		}
		if (!failReasons.add(frk)) {
			error("Duplicated " + frk + " detection.", reference);
		}
		if (confidence < -1 || confidence > 0) {
			error("Confidence value must be between -1 and 0", reference);
		}

	}

	private String getExpression(String type, EList<AbstractElement2> elements) {
		return getExpression(type, elements, 0);
	}

	private String getExpression(String type, EList<AbstractElement2> elements, int timestamp) {
		String expression = "";
		boolean derivative = false;
		int idxDevStart = 0;
		int idxDevEnd = 0;
		int devFrontParen = 0;
		int devBackParen = 0;
		AbstractElement2 element;
		for (int idx = 0; idx < elements.size(); idx++) {
			element = elements.get(idx);
			for (String parenthesis : element.getFrontParentheses()) {
				expression += parenthesis;
				if (derivative) {
					if (devFrontParen == 0 && idxDevEnd == 0) {
						idxDevStart = idx - 1;
						expression += "(";
					}
					devFrontParen++;
				}
			}
			if (element.getName() != null) {
				switch (type) {
				case "basic":
					expression += "1.0";
					break;
				}
			}
			if (element.getValue() != null) {
				expression += String.valueOf(element.getValue().getDVal());
			}
			if (element.getOp() != null) {
				for (Operators op : element.getOp()) {
					if (op.getOperator() != null) {
						expression += op.getOperator().getOp().toString();
					} else if (op.getComparation() != null) {
						expression += op.getComparation().getOp().toString();
					} else if (op.getLogicOperator() != null) {
						expression += op.getLogicOperator().getOp().toString();
					} else if (op.getBackParentheses() != null) {
						expression += op.getBackParentheses();
						if (derivative) {
							devBackParen++;
							if (devFrontParen == devBackParen) {
								if (idxDevEnd == 0) {
									idxDevEnd = idx;
									idx = idxDevStart;
									devFrontParen = 0;
									devBackParen = 0;
									expression += "-";
								} else {
									derivative = false;
									devFrontParen = 0;
									devBackParen = 0;
									idxDevEnd = 0;
									idxDevStart = 0;
									expression += ")";
									// expresion += "/" + timestamp + "-(" + prevTimestamp + ")";
									// => always 1 because of the normalization
								}
							}
						}
					}
				}
			}
			if (element.getMath() != null) {
				if (element.getMath().getLibrary().getCos() != null) {
					expression += "Math.cos";
				} else if (element.getMath().getLibrary().getSin() != null) {
					expression += "Math.sin";
				} else if (element.getMath().getLibrary().getModulus() != null) {
					expression += "Math.abs";
				} else if (element.getMath().getLibrary().getDerivative() != null) {
					switch (type) {
					case "basic":
						expression += "";
						break;
					}
				}
			}
		}
		return expression;
	}

	private Object evalExpression(String expression) {
		GraalJSScriptEngine engine = new GraalJSEngineFactory().getScriptEngine();
		try {
			Object obj = engine.eval(expression);
			if (obj != null && obj.getClass() == Integer.class) {
				return (double) ((int) obj);
			}
			return obj;
		} catch (ScriptException e) {
			e.printStackTrace();
			return null;
		}
	}

	private void resetParameters() {
		precond = null;
		pattern = null;
		tpattern = null;
		failReasons = new HashSet<Constants.FAILREASONS>();
	}

	// Avoids duplicated calls to @Check functions.
	@Override
	public void register(EValidatorRegistrar registrar) {
	}
}
