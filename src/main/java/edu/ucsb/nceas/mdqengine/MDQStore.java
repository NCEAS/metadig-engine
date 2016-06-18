package edu.ucsb.nceas.mdqengine;

import java.util.Collection;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Run;

public interface MDQStore {
	
	public Collection<String> listRecommendations();
	public Recommendation getRecommendation(String id);
	public void createRecommendation(Recommendation rec);
	public void updateRecommendation(Recommendation rec);
	public void deleteRecommendation(Recommendation rec);

	public Collection<String> listChecks();
	public Check getCheck(String id);
	public void createCheck(Check check);
	public void updateCheck(Check check);
	public void deleteCheck(Check check);
	
	public Collection<String> listRuns();
	public Run getRun(String id);
	public void createRun(Run run);
	public void deleteRun(Run run);

}
