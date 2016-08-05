package edu.ucsb.nceas.mdqengine.score;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Level;
import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Status;

public class Scorer {
	
	public static double FACTOR_SUCCESS = 1.0;
	public static double FACTOR_FAILURE = -0.5;
	public static double FACTOR_ERROR = -0.25;
	public static double FACTOR_SKIP = -0.0;

	public static int WEIGHT_INFO = 1;
	public static int WEIGHT_OPTIONAL = 2;
	public static int WEIGHT_REQUIRED = 3;
	
	/**
	 * Get number of results with given status
	 * @param status
	 * @return
	 */
	public static int getCount(Run run, final Status status) {
		
		Collection<Result> result = run.getResult();
		Predicate predicate = new Predicate() {			
			@Override
			public boolean evaluate(Object object) {
				return ((Result)object).getStatus().equals(status);
			}
		};
		int matches = CollectionUtils.countMatches(result, predicate);

		
		return matches;
	}
	
	/**
	 * Get the weighed score for this run.
	 * Takes into consideration the check level and status of result.
	 * A total score is computed and given over the total number of checks (weighted)
	 * resulting in higher scores that do better on more severe tests
	 * @param Run
	 * @return
	 */
	public static double getWeightedScore(Run run) {
		// keep track of what we have processed
		int count = 0;
		int weightedCount = 0;
		double total = 0;
		
		
		for (Result result : run.getResult()) {
			
			double score = 0;

			Level level = result.getCheck().getLevel();
			Status status = result.getStatus();
			
			
			switch (status) {
			case SUCCESS:
				score = 1 * FACTOR_SUCCESS;
				break;
				
			case FAILURE:
				score = 1 * FACTOR_FAILURE;
				break;
				
			case ERROR:
				score = 1 * FACTOR_ERROR;
				break;	
				
			case SKIP:
				score = 1 * FACTOR_SKIP;
				break;	
				
			default:
				score = 0;
				break;
			
			}
			
			// so weight it
			switch (level) {
			case INFO:
				weightedCount += WEIGHT_INFO;
				score = score * WEIGHT_INFO;
				break;
				
			case OPTIONAL:
				weightedCount += WEIGHT_OPTIONAL;
				score = score * WEIGHT_OPTIONAL;
				break;	
			
			case REQUIRED:
				weightedCount += WEIGHT_REQUIRED;
				score = score * WEIGHT_REQUIRED;
				break;

			default:
				weightedCount += 1;
				score = score * 1;
				break;
			}
			
			// now we have a weighted score to include in our total
			total = total + score;
			
			// keep track of all the results, unweighted, just in case
			count++;
				
		}
		
		// return the ratio
		double ratio = total/weightedCount;
		
		return ratio;
	}
	
	/**
	 * Calculate a composite score based on success, failure, errors and skips
	 * TODO: decide on a fair scoring rubric
	 * @param run
	 * @return
	 */
	public static double getCompositeScore(Run run) {
		double success = getCount(run, Status.SUCCESS);
		double failure = getCount(run, Status.FAILURE);
		double error = getCount(run, Status.ERROR);
		double skip = getCount(run, Status.SKIP);

		double sum = (success * FACTOR_SUCCESS)
				+ (failure * FACTOR_FAILURE) 
				+ (error * FACTOR_ERROR) 
				+ (skip * FACTOR_SKIP);
		int count = run.getResult().size();
		
		return sum/count;
		
	}
	
}
