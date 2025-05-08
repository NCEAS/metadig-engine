package edu.ucsb.nceas.mdqengine.model;

import org.apache.commons.lang.StringEscapeUtils;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a reusable quality check that is executed against metadata
 * documents using user-defined logic and extracted input values.
 * 
 * @author leinfelder
 *
 */
@XmlRootElement(name = "check", namespace = "https://nceas.ucsb.edu/mdqe/v1")
@XmlAccessorType(XmlAccessType.FIELD)
public class CheckV1 implements Check {

	/**
	 * The unique identifier for the check. The id can be used to reference
	 * previously defined checks within the same MDQ engine store. This is useful
	 * for common checks that should be shared across Suites
	 */
	@XmlElement(required = true)
	private String id;

	/**
	 * The short, but useful, name of the check. This will typically be how we
	 * colloquially refer to the check.
	 */
	@XmlElement(required = false)
	private String name;

	/**
	 * The longer more detailed explanation of what the check is doing. This might
	 * even include pseudo code or a summary of the actions performed by the
	 * Check.code
	 */
	@XmlJavaTypeAdapter(CDataAdapter.class)
	private String description;

	/**
	 * The type is meant to categorize the check. While we have not defined a
	 * vocabulary for check types, we envision some examples as: metadata, data,
	 * congruency. Currently, check writers are able to use any string they desire.
	 */
	@XmlElement(required = false)
	private String type;

	/**
	 * The level indicates how important this check is and must pull from the
	 * controlled Level vocabulary (INFO, OPTIONAL, REQUIRED). The level will help
	 * us better summarize the check results. For example, a REQUIRED check that
	 * fails is more severe than an INFO check that fails.
	 */
	@XmlElement(required = false)
	private Level level;

	/**
	 * The environment specifies what language the check code should be dispatched
	 * to. The environment, code and library elements should all agree so that
	 * syntax and dependencies can be appropriately met. This field is
	 * case-insensitive.
	 * Current options include:
	 * 'r' (the Renjin engine),
	 * 'rscript' (RScript command line),
	 * 'JavaScript' (the JavaScript engine)
	 * 'python' (the Jython engine)
	 * 'Java' (Java class implementations of Callable<Result>)
	 */
	@XmlElement(required = false)
	private String environment;

	/**
	 * The code for scripted environments (not 'Java') this is the code to be
	 * executed. When Check.environment is 'Java' this will be the fully qualified
	 * class name of the Callable<Result> implementation. The script code can take a
	 * few different forms, but should minimally return some value that will be
	 * captured as a single Result.output. The simplest form is just a series of
	 * script statements with the final statement returning the desired value for
	 * Result.output. Alternatively, a call() function can be defined that returns
	 * the desired Result.output. This function will be called automatically if no
	 * output is found when executing the script as a series of statements. If both
	 * output and status need to be returned, the code can set 'output' and 'status'
	 * variables and those will be included in the Result. Finally - for greatest
	 * control - a full instance of the Result class can be returned in which case
	 * the code writer is responsible for setting the appropriate Result fields in a
	 * variable named 'mdq_result'.
	 */
	@XmlJavaTypeAdapter(CDataAdapter.class)
	private String code;

	/**
	 * One or more external script libraries can be included using their URL.
	 * Caution should be taken that these URLs are trusted so that malicious code
	 * cannot be executed by the engine.
	 */
	@XmlElement(required = false)
	private List<URL> library;

	/**
	 * Be default, each check is entirely independent and does not share the script
	 * environment of any previously executed checks in the Suite. If checks need to
	 * reference variables or functions defined in earlier checks within the same
	 * suite, they can set this flag to 'true'. Care should be taken when inheriting
	 * state such that variable name collisions do not cause unintended results.
	 */
	@XmlElement(required = false)
	private Boolean inheritState = false;

	/**
	 * Selectors are used to extract certain parts of the metadata document and make
	 * those values available to the Check.code. Each selector should have a unique
	 * name within the same check since the Selector.name is used to create a global
	 * variable in the scripting environment. The code can then reference that
	 * variable name (exactly!) and gain access to the value (if it exists). The
	 * variables can be strings, numbers, booleans and lists of those types. When a
	 * selector does not locate a value in the document, a `null` value will be
	 * provided (however that is represented in the particular script environment).
	 */
	@XmlElement(required = false)
	private List<SelectorV1> selector;

	/**
	 * The optional dialect list can be used to identify checks as only pertaining
	 * to specific metadata types. Instead of constraining this to a pre-determined
	 * list of dialects, they are self-defined by Dialect.xpath such that a dialect
	 * can use any xpath expression to determine if a document is indeed that
	 * dialect. Typically, we will use namespace comparisons, but there is
	 * technically no limit of how a dialect can determine what it is. If a document
	 * is in the dialect list, the check will not be run and Result.status will be
	 * set to Status.SKIP. If no Dialect[s] are provided, it is assumed that the
	 * check applies to any and all metadata documents.
	 */
	@XmlElement(required = false)
	private List<DialectV1> dialect;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getDescription() {
		return StringEscapeUtils.unescapeXml(description);
	}

	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public Level getLevel() {
		return level;
	}

	@Override
	public void setLevel(Level level) {
		this.level = level;
	}

	public String getEnvironment() {
		return environment;
	}

	@Override
	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	@Override
	public String getCode() {
		return (StringEscapeUtils.unescapeXml(code));
	}

	@Override
	public void setCode(String code) {
		this.code = code;
	}

	@Override
	public List<? extends Selector> getSelector() {
		return selector;
	}

	@Override
	public void setSelector(List<? extends Selector> selector) {
		this.selector = selector.stream()
				.map(s -> (SelectorV1) s)
				.collect(Collectors.toList());
	}

	@Override
	public List<? extends Dialect> getDialect() {
		return dialect;
	}

	@Override
	public void setDialect(List<? extends Dialect> dialect) {
		this.dialect = dialect.stream()
				.map(s -> (DialectV1) s)
				.collect(Collectors.toList());
	}

	@Override
	public List<URL> getLibrary() {
		return library;
	}

	@Override
	public void setLibrary(URL... library) {
		this.library = Arrays.asList(library);
	}

	@Override
	public void setLibrary(List<URL> library) {
		this.library = library;
	}

	@Override
	public Boolean isInheritState() {
		return inheritState;
	}

	@Override
	public void setInheritState(Boolean inheritState) {
		this.inheritState = inheritState;
	}
}
