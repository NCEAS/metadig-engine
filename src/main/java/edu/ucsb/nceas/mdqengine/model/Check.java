package edu.ucsb.nceas.mdqengine.model;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.eclipse.persistence.oxm.annotations.XmlCDATA;

@XmlRootElement
@XmlType(propOrder = {"id", "name", "description", "type", "level", "environment", "code", "library", "inheritState", "selector", "dialect"})
public class Check {
	
	private String id;

	private String name;
	
	@XmlCDATA
	private String description;

	private String type;
	
	private Level level;
	
	private String environment;

	@XmlCDATA
	private String code;
	
	private List<URL> library;
	
	private Boolean inheritState = false;
	
	private List<Selector> selector;
	
	private List<Dialect> dialect;

	@XmlElement(required = true)
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@XmlElement(required = true)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlElement(required = false)
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@XmlElement(required = false)
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@XmlElement(required = true)
	public Level getLevel() {
		return level;
	}

	public void setLevel(Level level) {
		this.level = level;
	}

	@XmlElement(required = true)
	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	@XmlElement(required = false)
	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	@XmlElement(required = false)
	public List<Selector> getSelector() {
		return selector;
	}

	public void setSelector(List<Selector> selector) {
		this.selector = selector;
	}

	@XmlElement(required = false)
	public List<Dialect> getDialect() {
		return dialect;
	}

	public void setDialect(List<Dialect> dialect) {
		this.dialect = dialect;
	}

	@XmlElement(required = false)
	public List<URL> getLibrary() {
		return library;
	}

	public void setLibrary(URL... library) {
		this.library = Arrays.asList(library);
	}
	
	public void setLibrary(List<URL> library) {
		this.library = library;
	}

	@XmlElement(required = false)
	public Boolean isInheritState() {
		return inheritState;
	}

	public void setInheritState(Boolean inheritState) {
		this.inheritState = inheritState;
	}
}
