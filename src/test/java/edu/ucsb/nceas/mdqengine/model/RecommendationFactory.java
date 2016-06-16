package edu.ucsb.nceas.mdqengine.model;

import java.util.ArrayList;
import java.util.List;

import edu.ucsb.nceas.mdqengine.model.Check;
import edu.ucsb.nceas.mdqengine.model.Recommendation;
import edu.ucsb.nceas.mdqengine.model.Selector;

public class RecommendationFactory {

	public static Recommendation getMockRecommendation() {
		// make a recommendation
		Recommendation recommendation = new Recommendation();
		recommendation.setName("Testing suite");

		// set-up checks
		List<Check> checks = new ArrayList<Check>();

		// title
		Check titleCheck = new Check();
		titleCheck.setName("titleLength");
		titleCheck.setEnvironment("r");
		titleCheck.setLevel("WARN");
		List<Selector> selectors = new ArrayList<Selector>();
		Selector s1 = new Selector();
		s1.setName("title");
		s1.setXpath("//dataset/title");
		selectors.add(s1);
		titleCheck.setSelector(selectors);
		titleCheck.setCode("nchar(title) > 10");
		titleCheck.setExpected("TRUE");
		checks.add(titleCheck);

		// entityCount
		Check entityCount = new Check();
		entityCount.setName("entityCount");
		entityCount.setEnvironment("JavaScript");
		entityCount.setLevel("INFO");
		selectors = new ArrayList<Selector>();
		s1 = new Selector();
		s1.setName("entityCount");
		s1.setXpath("count(//dataset/dataTable | //dataset/otherEntity)");
		selectors.add(s1);
		entityCount.setSelector(selectors);
		entityCount.setCode("entityCount > 0");
		entityCount.setExpected("true");
		checks.add(entityCount);

		// attributeNames
		Check attributeNames = new Check();
		attributeNames.setName("attributeNames");
		attributeNames.setEnvironment("r");
		attributeNames.setLevel("ERROR");
		selectors = new ArrayList<Selector>();
		s1 = new Selector();
		s1.setName("attributeNames");
		s1.setXpath("//attribute/attributeName");
		selectors.add(s1);
		attributeNames.setSelector(selectors);
		attributeNames.setCode("any(duplicated(attributeNames))");
		attributeNames.setExpected("FALSE");
		checks.add(attributeNames);

		recommendation.setCheck(checks);

		return recommendation;
	}
}
