package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.script.ScriptException;

import org.apache.commons.beanutils.BeanUtils;

public class JavaDispatcher extends Dispatcher {
		
	@Override
	public DispatchResult dispatch(Map<String, Object> names, String className) throws ScriptException {

		DispatchResult results = null;
		try {
			// create instance of the given class - must be impl of Callable<String>
			Class clazz = Class.forName(className);
			log.debug("Calling class: " + className);
			Callable<DispatchResult> runner = (Callable<DispatchResult>) clazz.newInstance();
			// set the properties from name/value Map
			for (Entry<String, Object> entry: names.entrySet()) {
				log.debug("Setting property: " + entry.getKey() + "=" + entry.getValue());
				BeanUtils.setProperty(runner, entry.getKey(), entry.getValue());
			}
			// call the bean, blocking for results
			ExecutorService exec = Executors.newSingleThreadExecutor();
			Future<DispatchResult> future = exec.submit(runner);
			results = future.get();
			
		} catch (Exception e) {
			throw new ScriptException(e);
		}
		
		log.debug("Result: " + results.getValue());

		return results;
	}

}
