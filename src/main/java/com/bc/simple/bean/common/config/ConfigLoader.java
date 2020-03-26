package com.bc.simple.bean.common.config;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.bc.simple.bean.common.util.StringUtils;

public class ConfigLoader {

	public static class IllegalConfigException extends RuntimeException {
		private static final long serialVersionUID = 1434072254199249298L;

		public IllegalConfigException(String msg) {
			super(msg);
		}

		public IllegalConfigException() {
			super();
		}

		public IllegalConfigException(Throwable cause) {
			super(cause);
		}

		public IllegalConfigException(String msg, Throwable cause) {
			super(msg, cause);
		}

	}

	public static class Node implements Serializable {
		private static final long serialVersionUID = 3441144908360271564L;

		private Node parent;
		private final List<Node> childs = new ArrayList<Node>();
		private final Map<String, Object> attributes;
		private String name;

		public Node(String name) {
			this.name = name;
			this.attributes = new HashMap<String, Object>();
		}

		public Node() {
			this("");
		}

		public Node getParent() {
			return parent;
		}

		public void setParent(Node parent) {
			this.parent = parent;
		}

		public List<Node> getChilds() {
			return childs;
		}

		public void addChild(Node child) {
			this.childs.add(child);
		}

		public Object attr(String key) {
			return attributes.get(key);
		}

		public Map<String, Object> attrs() {
			return attributes;
		}

		public String attrString(String key) {
			Object value = attributes.get(key);
			if (value == null) {
				return StringUtils.EMPTY;
			} else {
				return value.toString();
			}
		}

		public boolean hasAttr(String attr) {
			return attributes.containsKey(attr);
		}

		public Object attr(String key, Object value) {
			return attributes.put(key, value);
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return toString(StringUtils.EMPTY);
		}

		private String toString(String identation) {
			StringBuffer sb = new StringBuffer();
			sb.append(identation).append("Node:").append(this.name).append("\n");
			for (Entry<String, Object> attribute : attributes.entrySet()) {
				sb.append(identation).append("\t").append(attribute.getKey()).append(":").append(attribute.getValue())
						.append("\n");
			}
			for (Node node : childs) {
				sb.append(node.toString("\t" + identation)).append("\n");
			}
			return sb.toString();
		}

	}

	public static void main(String[] args) throws IOException {
		Node root = load(new File(ClassLoader.getSystemResource("sample.config").getFile()));
		System.out.println(root);
	}

	public static Node load(File configFile) {
		try {
			return load(new String(Files.readAllBytes(configFile.toPath())));
		} catch (IOException e) {
			throw new IllegalConfigException(e);
		}
	}

	/**
	 * @param config String
	 *               <p>
	 *               we use a method of level iteration which is discriminated by en
	 *               double space char
	 *               <p>
	 * @return {@link Node}
	 */
	public static Node load(String config) {
		config = config + "\n";
		StringBuffer line = new StringBuffer();
		Node root = new Node("root");
		Node current = root;
		int level = 0, curLevel = 0;
		for (int i = 0; i < config.length(); i++) {
			if (config.charAt(i) == '\n') {
				String ln = line.toString().trim();
				if (StringUtils.isEmpty(ln) || ln.charAt(0) == '#') {
					level = 0;
					line.delete(0, line.length());
					continue;
				}
				if (level > curLevel) {
					throw new IllegalConfigException(line.toString());
				}

				for (; level < curLevel; curLevel--) {
					current = current.getParent();
				}

				if (ln.indexOf(":") >= 0) {
					attr(ln, current);
				} else {
					current = addChild(ln.trim(), current);
					curLevel++;
				}
				line.delete(0, line.length());
				level = 0;
			} else if (config.charAt(i) == '\t') {
				level++;
			}
			line.append(config.charAt(i));
		}
		return root;
	}

	private static void attr(String line, Node node) {
		String[] attr = line.toString().split(":");
		if (attr.length == 0) {
			throw new IllegalConfigException(line.toString());
		} else if (attr.length == 1) {
			node.attr(attr[0], "");
		} else {
			node.attr(attr[0], attr[1]);
		}
	}

	private static Node addChild(String line, Node parent) {
		Node node = new Node(line.toString().trim());
		node.setParent(parent);
		parent.addChild(node);
		return node;
	}

}
