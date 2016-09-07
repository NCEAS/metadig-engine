package edu.ucsb.nceas.mdqengine.model;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.eclipse.persistence.oxm.annotations.XmlCDATA;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"id", "name", "description", "type", "level", "environment", "code", "library", "inheritState", "selector", "dialect"})
public class Check {
	
	@XmlElement(required = true)
	private String id;

	@XmlElement(required = false)
	private String name;
	
	@XmlCDATA
	private String description;

	@XmlElement(required = false)
	private String type;
	
	@XmlElement(required = false)
	private Level level;

	@XmlElement(required = false)
	private String environment;

	@XmlCDATA
	private String code;
	
	@XmlElement(required = false)
	private List<URL> library;
	
	@XmlElement(required = false)
	private Boolean inheritState = false;
	
	@XmlElement(required = false)
	private List<Selector> selector;
	
	@XmlElement(required = false)
	private List<Dialect> dialect;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Level getLevel() {
		return level;
	}

	public void setLevel(Level level) {
		this.level = level;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public List<Selector> getSelector() {
		return selector;
	}

	public void setSelector(List<Selector> selector) {
		this.selector = selector;
	}

	public List<Dialect> getDialect() {
		return dialect;
	}

	public void setDialect(List<Dialect> dialect) {
		this.dialect = dialect;
	}

	public List<URL> getLibrary() {
		return library;
	}

	public void setLibrary(URL... library) {
		this.library = Arrays.asList(library);
	}
	
	public void setLibrary(List<URL> library) {
		this.library = library;
	}

	public Boolean isInheritState() {
		return inheritState;
	}

	public void setInheritState(Boolean inheritState) {
		this.inheritState = inheritState;
	}
}
