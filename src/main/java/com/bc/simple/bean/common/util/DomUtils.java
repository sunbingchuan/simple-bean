package com.bc.simple.bean.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.bc.simple.bean.common.config.ConfigLoader.Node;

public class DomUtils {

	/**
	 * Retrieves all child elements of the given DOM element that match any of the
	 * given element names. Only looks at the direct child level of the given
	 * element; do not go into further depth (in contrast to the DOM API's
	 * {@code getElementsByTagName} method).
	 * 
	 * @param ele           the DOM element to analyze
	 * @param childEleNames the child element names to look for
	 * @return a List of child {@code org.w3c.dom.Element} instances
	 * @see org.w3c.dom.Element
	 * @see org.w3c.dom.Element#getElementsByTagName
	 */
	public static List<Node> getChildElementsByTagName(Node ele, String... childEleNames) {
		List<String> childEleNameList = Arrays.asList(childEleNames);
		List<Node> nl = ele.getChilds();
		List<Node> childEles = new ArrayList<>();
		for (Node node : nl) {
			if (nodeNameMatch(node, childEleNameList)) {
				childEles.add(node);
			}
		}
		return childEles;
	}

	/**
	 * Retrieves all child elements of the given DOM element that match the given
	 * element name. Only look at the direct child level of the given element; do
	 * not go into further depth (in contrast to the DOM API's
	 * {@code getElementsByTagName} method).
	 * 
	 * @param ele          the DOM element to analyze
	 * @param childEleName the child element name to look for
	 * @return a List of child {@code org.w3c.dom.Element} instances
	 * @see org.w3c.dom.Element
	 * @see org.w3c.dom.Element#getElementsByTagName
	 */
	public static List<Node> getChildElementsByTagName(Node ele, String childEleName) {
		return getChildElementsByTagName(ele, new String[] { childEleName });
	}

	/**
	 * Utility method that returns the first child element identified by its name.
	 * 
	 * @param ele          the DOM element to analyze
	 * @param childEleName the child element name to look for
	 * @return the {@code org.w3c.dom.Element} instance, or {@code null} if none
	 *         found
	 */
	public static Node getChildElementByTagName(Node ele, String childEleName) {
		List<Node> nl = ele.getChilds();
		for (Node node : nl) {
			if (nodeNameMatch(node, childEleName)) {
				return node;
			}
		}
		return null;
	}

	/**
	 * Utility method that returns the first child element value identified by its
	 * name.
	 * 
	 * @param ele          the DOM element to analyze
	 * @param childEleName the child element name to look for
	 * @return the extracted text value, or {@code null} if no child element found
	 */
	public static String getChildElementValueByTagName(Node ele, String childEleName) {
		Node child = getChildElementByTagName(ele, childEleName);
		return (child != null ? getTextValue(child) : null);
	}

	/**
	 * Retrieves all child elements of the given DOM element.
	 * 
	 * @param ele the DOM element to analyze
	 * @return a List of child {@code org.w3c.dom.Element} instances
	 */
	public static List<Node> getChildElements(Node ele) {
		return ele.getChilds();
	}

	/**
	 * Extracts the text value from the given DOM element, ignoring XML comments.
	 * <p>
	 * Appends all CharacterData nodes and EntityReference nodes into a single
	 * String value, excluding Comment nodes. Only exposes actual user-specified
	 * text, no default values of any kind.
	 * 
	 * @see CharacterData
	 * @see EntityReference
	 * @see Comment
	 */
	public static String getTextValue(Node valueEle) {
		return valueEle.attrString("text");
	}

	/**
	 * Namespace-aware equals comparison. Returns {@code true} if either
	 * {@link Node#getLocalName} or {@link Node#getNodeName} equals
	 * {@code desiredName}, otherwise returns {@code false}.
	 */
	public static boolean nodeNameEquals(Node node, String desiredName) {
		return nodeNameMatch(node, desiredName);
	}

	/**
	 * Matches the given node's name and local name against the given desired name.
	 */
	private static boolean nodeNameMatch(Node node, String desiredName) {
		return desiredName.equals(node.getName());
	}

	/**
	 * Matches the given node's name and local name against the given desired names.
	 */
	private static boolean nodeNameMatch(Node node, Collection<?> desiredNames) {
		return desiredNames.contains(node.getName());
	}

}
