package com.bc.simple.bean.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.bc.simple.bean.common.config.ConfigLoader.Node;

public class DomUtils {


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


	public static List<Node> getChildElementsByTagName(Node ele, String childEleName) {
		return getChildElementsByTagName(ele, new String[] {childEleName});
	}


	public static Node getChildElementByTagName(Node ele, String childEleName) {
		List<Node> nl = ele.getChilds();
		for (Node node : nl) {
			if (nodeNameMatch(node, childEleName)) {
				return node;
			}
		}
		return null;
	}


	public static String getChildElementValueByTagName(Node ele, String childEleName) {
		Node child = getChildElementByTagName(ele, childEleName);
		return (child != null ? getTextValue(child) : null);
	}


	public static List<Node> getChildElements(Node ele) {
		return ele.getChilds();
	}


	public static String getTextValue(Node valueEle) {
		return valueEle.attrString("text");
	}


	public static boolean nodeNameEquals(Node node, String desiredName) {
		return nodeNameMatch(node, desiredName);
	}


	private static boolean nodeNameMatch(Node node, String desiredName) {
		return desiredName.equals(node.getName());
	}


	private static boolean nodeNameMatch(Node node, Collection<?> desiredNames) {
		return desiredNames.contains(node.getName());
	}

}
