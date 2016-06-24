package edu.ucsb.nceas.mdqengine.score;

import java.util.Collection;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

import edu.ucsb.nceas.mdqengine.model.Result;
import edu.ucsb.nceas.mdqengine.model.Run;
import edu.ucsb.nceas.mdqengine.model.Status;

public class Scorer {

	/**
	 * Get ratio of status/total check results for this run
	 * Depending on the status, a higher ratio will be better (SUCCESS) or worse (FAILURE)
	 * @param status
	 * @return
	 */
	public static double getScore(Run run, final Status status) {
		double score = 0;
		
		Collection<Result> result = run.getResult();
		Predicate predicate = new Predicate() {			
			@Override
			public boolean evaluate(Object object) {
				return ((Result)object).getStatus().equals(status);
			}
		};
		int matches = CollectionUtils.countMatches(result, predicate);
		score = matches/result.size();

		
		return score;
	}
	
	/**
	 * Calculate a composite score based on success, failure, errors and skips
	 * TODO: decide on a fair scoring rubric
	 * @param run
	 * @return
	 */
	public static double getCompositeScore(Run run) {
		double success = getScore(run, Status.SUCCESS);
		double failure = getScore(run, Status.FAILURE);
		double error = getScore(run, Status.ERROR);
		double skip = getScore(run, Status.SKIP);

		return success;
		
	}
}
