package edu.ucsb.nceas.mdqengine.model;

import java.net.URL;
import java.util.List;

public interface Check {

    public String getId();
	public void setId(String id);
	public String getName();
	public void setName(String name);
	public String getDescription();
	public void setDescription(String description);
	public String getType();
	public void setType(String type);
	public Level getLevel();
	public void setLevel(Level level);
	public String getEnvironment();
	public void setEnvironment(String environment);
	public String getCode();
	public void setCode(String code);
    List<? extends Selector> getSelector();
	public void setSelector(List<? extends Selector> selector);
	public List<Dialect> getDialect();
	public void setDialect(List<Dialect> dialect);
	public List<URL> getLibrary();
	public void setLibrary(URL... library);
	public void setLibrary(List<URL> library);
	public Boolean isInheritState();
	public void setInheritState(Boolean inheritState);
}
