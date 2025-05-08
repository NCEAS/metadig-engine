package edu.ucsb.nceas.mdqengine.model;

import java.net.URL;
import java.util.List;

/**
 * Represents a reusable quality check that is executed against metadata
 * documents using user-defined logic and extracted input values.
 * 
 * A Check performs its logic by accessing metadata values via one or more
 * Selectors, then executing a code block (script or Java class) in a
 * defined runtime environment.
 * 
 * Each check includes:
 * <ul>
 * <li>A unique id for reference and reuse</li>
 * <li>A short name and a longer description</li>
 * <li>A type categorizing the check (e.g., findable, accessible,
 * interoperable)</li>
 * <li>A level indicating the check's importance (INFO, OPTIONAL, REQUIRED)</li>
 * <li>An environment such as Java, R, Python, or JavaScript</li>
 * <li>A code block or Java class that implements the logic</li>
 * <li>Optional library URLs for external script dependencies</li>
 * <li>An inheritState flag for sharing variables between checks</li>
 * <li>One or more Selectors that extract values from the document</li>
 * <li>Dialects used to conditionally apply the check or ensure Check is valid
 * for a document</li>
 * </ul>
 *
 * @author leinfelder
 */
public interface Check {

	/**
	 * Gets the unique identifier for the check.
	 * 
	 * @return the check ID
	 */
	public String getId();

	/**
	 * Sets the unique identifier for the check.
	 * 
	 * @param id the check ID
	 */
	public void setId(String id);

	/**
	 * Gets the name of the check.
	 * 
	 * @return the name of the check
	 */
	public String getName();

	/**
	 * Sets the name of the check.
	 * 
	 * @param name the name of the check
	 */
	public void setName(String name);

	/**
	 * Gets the description of the check.
	 * 
	 * @return the check description
	 */
	public String getDescription();

	/**
	 * Sets the description of the check.
	 * 
	 * @param description the check description
	 */
	public void setDescription(String description);

	/**
	 * Gets the type of the check.
	 * 
	 * @return the check type
	 */
	public String getType();

	/**
	 * Sets the type of the check.
	 * 
	 * @param type the check type
	 */
	public void setType(String type);

	/**
	 * Gets the level of the check (INFO, OPTIONAL, REQUIRED).
	 * 
	 * @return the level of the check
	 */
	public Level getLevel();

	/**
	 * Sets the level of the check (INFO, OPTIONAL, REQUIRED).
	 * 
	 * @param level the check level
	 */
	public void setLevel(Level level);

	/**
	 * Gets the execution environment for the check code (e.g., java, rscript,
	 * python).
	 * 
	 * @return the environment name
	 */
	public String getEnvironment();

	/**
	 * Sets the execution environment for the check code.
	 * 
	 * @param environment the environment name
	 */
	public void setEnvironment(String environment);

	/**
	 * Gets the code or script executed by the check.
	 * 
	 * @return the check code
	 */
	public String getCode();

	/**
	 * Sets the code or script executed by the check.
	 * 
	 * @param code the check code
	 */
	public void setCode(String code);

	/**
	 * Gets the list of selectors used to extract metadata values.
	 * 
	 * @return the list of selectors
	 */
	List<? extends Selector> getSelector();

	/**
	 * Sets the list of selectors used to extract metadata values.
	 * 
	 * @param selector the list of selectors
	 */
	public void setSelector(List<? extends Selector> selector);

	/**
	 * Gets the list of dialects that this check applies to.
	 * 
	 * @return the list of dialects
	 */
	public List<? extends Dialect> getDialect();

	/**
	 * Sets the list of dialects that this check applies to.
	 * 
	 * @param dialect the list of dialects
	 */
	public void setDialect(List<? extends Dialect> dialect);

	/**
	 * Gets the list of external script libraries used by the check.
	 * 
	 * @return the list of library URLs
	 */
	public List<URL> getLibrary();

	/**
	 * Sets the external script libraries using a varargs list of URLs.
	 * 
	 * @param library one or more library URLs
	 */
	public void setLibrary(URL... library);

	/**
	 * Sets the external script libraries using a list of URLs.
	 * 
	 * @param library the list of library URLs
	 */
	public void setLibrary(List<URL> library);

	/**
	 * Indicates whether the check inherits state from previous checks in the suite.
	 * 
	 * @return true if state is inherited; false otherwise
	 */
	public Boolean isInheritState();

	/**
	 * Sets whether the check should inherit state from previous checks in the
	 * suite.
	 * 
	 * @param inheritState true to inherit state; false otherwise
	 */
	public void setInheritState(Boolean inheritState);
}
