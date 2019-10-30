package com.bc.simple.bean.common.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bc.simple.bean.common.util.StringUtils;

public class ExpressionUtils {
	private static final List<Object> orders = new ArrayList<Object>(16);
	private static final char init = ' ';
	private static final String EXPRESSION_FINDER = "([\\d\\+\\-\\*\\/\\^\\=\\!\\)\\(%><]+)([^\\d\\+\\-\\*\\/\\^\\=\\!\\)\\(%><]*)";

	static {
		addNode(0, new NumNode(init));
		addNode(1, new PointNode(init));
		addNode(2, new SignNode(init));
		addNode(3, new MultiplyNode(init), true);
		addNode(3, new DivideNode(init), true);
		addNode(3, new ModNode(init), true);
		addNode(4, new PlusNode(init), true);
		addNode(4, new MinusNode(init), true);
		addNode(5, new EqualNode(init), true);
		addNode(5, new GreaterNode(init), true);
		addNode(5, new LessNode(init), true);
		addNode(6, new NotNode(init), true);
		addNode(7, new AndNode(init), true);
		addNode(7, new XorNode(init), true);
		addNode(7, new OrNode(init), true);
		addNode(8, new BracketNode(init));
	}

	public static interface Node {
		public int getLevel();

		boolean nodeIs(char c);

		public Object react();
	}

	public abstract static class BaseNode implements Node {
		protected StringBuffer content = new StringBuffer();

		protected BaseNode prev, next;

		protected volatile Boolean reacted = false;

		protected int level = -1;

		@SuppressWarnings({ "rawtypes", "unused" })
		@Override
		public int getLevel() {
			if (level < 0) {
				a: for (int i = 0; i < orders.size(); i++) {
					Object node = orders.get(i);
					if (node.getClass().equals(this.getClass())) {
						level = i;
						break;
					} else if (node instanceof List) {
						for (Object nd : (List) node) {
							if (nd.getClass().equals(this.getClass())) {
								level = i;
								break a;
							}
						}
					}
				}
			}

			return level;
		}

		public BaseNode getPrev() {
			return prev;
		}

		public void setPrev(BaseNode prev) {
			this.prev = prev;
			if (prev != null) {
				prev.next = this;
			}
		}

		public BaseNode getNext() {
			return next;
		}

		public void setNext(BaseNode next) {
			this.next = next;
			if (next != null) {
				next.prev = this;
			}
		}

		public abstract BaseNode multiply(char c);

		protected Object up() {
			return up(this);
		}

		protected Object up(BaseNode node) {
			return up(node, true);
		}

		protected Object up(BaseNode node, boolean inc) {
			if (node == null) {
				return null;
			}
			if (node.prev != null && node.next != null) {
				if (!inc || (inc && node.prev.getLevel() > node.getLevel() && node.next.getLevel() > node.getLevel())) {
					if (node.prev.getLevel() <= node.next.getLevel()) {
						if (!node.prev.reacted) {
							return node.prev.react();
						}
					} else {
						if (!node.next.reacted) {
							return node.next.react();
						}
					}
				}
			} else if (node.prev != null && (!inc || (inc && node.prev.getLevel() > node.getLevel()))
					&& !node.prev.reacted) {
				return node.prev.react();
			} else if (node.next != null && (!inc || (inc && node.next.getLevel() > node.getLevel()))
					&& !node.next.reacted) {
				return node.next.react();
			}
			return null;
		}

		protected void destroy(BaseNode node) {
			if (node.prev != null && node.next != null) {
				node.prev.setNext(node.next);
			} else if (node.prev != null) {
				node.prev.setNext(null);
			} else if (node.next != null) {
				node.next.setPrev(null);
			}
		}

		protected int groupAndCompare(BaseNode l, BaseNode r) {
			l.content.append(r.content);
			r.content = l.content;
			l.reacted = true;
			r.reacted = true;
			String left = l.prev.react().toString();
			String right = r.next.react().toString();
			return compare(left, right);
		}

		protected Boolean logicalGroupAndCompare(BaseNode l, BaseNode r) {
			l.content.append(r.content);
			r.content = l.content;
			l.reacted = true;
			r.reacted = true;
			Boolean left = Boolean.valueOf(l.prev.react().toString());
			Boolean right = Boolean.valueOf(r.next.react().toString());
			System.out.println(left + l.content.toString() + right);
			switch (l.content.toString()) {
			case "||":
				return left | right;
			case "&&":
				return left & right;
			case "!=":
				return left != right;
			default:
				break;
			}
			return null;
		}

		protected int compare(String left, String right) {
			System.out.println(left + content.toString() + right);
			BigDecimal leftOperand = new BigDecimal(left);
			BigDecimal rightOperand = new BigDecimal(right);
			return leftOperand.compareTo(rightOperand);
		}

		@Override
		public String toString() {
			return content.toString();
		}
	}

	public static class NumNode extends BaseNode {

		public NumNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						while (this.prev != null && this.prev.getLevel() <= getLevel() && !this.prev.reacted) {
							content.insert(0, this.prev.react().toString());
							setPrev(this.prev.prev);
						}
						while (this.next != null && this.next.getLevel() <= getLevel() && !this.next.reacted) {
							content.append(this.next.react().toString());
							setNext(this.next.next);
						}
						Object upObject = up();
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public boolean nodeIs(char c) {
			return Character.isDigit(c);
		}

		@Override
		public BaseNode multiply(char c) {
			return new NumNode(c);
		}

	}

	public static class PointNode extends BaseNode {

		public PointNode(char c) {
			content.append(c);
		}

		@Override
		public boolean nodeIs(char c) {
			return c == '.';
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.prev != null && this.prev.getLevel() < getLevel()) {
							content.insert(0, this.prev.react().toString());
							setPrev(this.prev.prev);
						}
						if (this.next != null && this.next.getLevel() < getLevel()) {
							content.append(this.next.react().toString());
							setNext(this.next.next);
						}
						Object upObject = up();
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public BaseNode multiply(char c) {
			return new PointNode(c);
		}
	}

	public static class SignNode extends BaseNode {

		public SignNode(char c) {
			if (c == '-') {
				content.append(c);
			}
		}

		@Override
		public boolean nodeIs(char c) {
			return (c == '+' || c == '-') && (prev == null || (!(prev instanceof NumNode)
					&& !((prev instanceof BracketNode) && (!((BracketNode) prev).isBegin()))));
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.next instanceof NumNode || this.next instanceof PointNode) {
							this.content.append(this.next.react().toString());
							setNext(this.next.next);
						}
						Object upObject = up();
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public BaseNode multiply(char c) {
			return new SignNode(c);
		}

	}

	public static class MultiplyNode extends BaseNode {

		@Override
		public boolean nodeIs(char c) {
			return c == '*';
		}

		public MultiplyNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						String left = this.prev.react().toString();
						String right = this.next.react().toString();
						System.out.println(left + content.toString() + right);
						BigDecimal leftOperand = new BigDecimal(left);
						BigDecimal rightOperand = new BigDecimal(right);
						BigDecimal result = leftOperand.multiply(rightOperand);
						content.delete(0, content.capacity());
						content.append(result.toString());
						setPrev(this.prev.prev);
						setNext(this.next.next);
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public BaseNode multiply(char c) {
			return new MultiplyNode(c);
		}
	}

	public static class DivideNode extends BaseNode {

		@Override
		public boolean nodeIs(char c) {
			return c == '/';
		}

		public DivideNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						String left = this.prev.react().toString();
						String right = this.next.react().toString();
						System.out.println(left + content.toString() + right);
						BigDecimal leftOperand = new BigDecimal(left);
						BigDecimal rightOperand = new BigDecimal(right);
						BigDecimal result = leftOperand.divide(rightOperand);
						content.delete(0, content.capacity());
						content.append(result.toString());
						setPrev(this.prev.prev);
						setNext(this.next.next);
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public BaseNode multiply(char c) {
			return new DivideNode(c);
		}

	}

	public static class ModNode extends BaseNode {

		@Override
		public boolean nodeIs(char c) {
			return c == '%';
		}

		public ModNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						String left = this.prev.react().toString();
						String right = this.next.react().toString();
						System.out.println(left + content.toString() + right);
						BigDecimal leftOperand = new BigDecimal(left);
						BigDecimal rightOperand = new BigDecimal(right);
						BigDecimal result = leftOperand.remainder(rightOperand);
						content.delete(0, content.capacity());
						content.append(result.toString());
						setPrev(this.prev.prev);
						setNext(this.next.next);
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public BaseNode multiply(char c) {
			return new ModNode(c);
		}
	}

	public static class PlusNode extends BaseNode {

		@Override
		public boolean nodeIs(char c) {
			return c == '+';
		}

		public PlusNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						String left = this.prev.react().toString();
						String right = this.next.react().toString();
						System.out.println(left + content.toString() + right);
						BigDecimal leftOperand = new BigDecimal(left);
						BigDecimal rightOperand = new BigDecimal(right);
						BigDecimal result = leftOperand.add(rightOperand);
						content.delete(0, content.capacity());
						content.append(result.toString());
						setPrev(this.prev.prev);
						setNext(this.next.next);
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public BaseNode multiply(char c) {
			return new PlusNode(c);
		}

	}

	public static class MinusNode extends BaseNode {

		@Override
		public boolean nodeIs(char c) {
			return c == '-';
		}

		public MinusNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						String left = this.prev.react().toString();
						String right = this.next.react().toString();
						System.out.println(left + content.toString() + right);
						BigDecimal leftOperand = new BigDecimal(left);
						BigDecimal rightOperand = new BigDecimal(right);
						BigDecimal result = leftOperand.subtract(rightOperand);
						content.delete(0, content.capacity());
						content.append(result.toString());
						setPrev(this.prev.prev);
						setNext(this.next.next);
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return content.toString();
		}

		@Override
		public BaseNode multiply(char c) {
			return new MinusNode(c);
		}
	}

	public static class AndNode extends BaseNode {
		private Boolean result = null;

		@Override
		public boolean nodeIs(char c) {
			return c == '&';
		}

		public AndNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.next instanceof AndNode) {
							result = logicalGroupAndCompare(this, this.next);
							setPrev(this.prev.prev);
							setNext(this.next.next.next);
						} else if (this.prev instanceof AndNode) {
							result = logicalGroupAndCompare(this.prev, this);
							setPrev(this.prev.prev.prev);
							setNext(this.next.next);
						} else {
							Boolean left = Boolean.valueOf(this.prev.react().toString());
							Boolean right = Boolean.valueOf(this.next.react().toString());
							System.out.println(left + content.toString() + right);
							setPrev(this.prev.prev);
							setNext(this.next.next);
							result = left & right;
						}
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return result;
		}

		@Override
		public BaseNode multiply(char c) {
			return new AndNode(c);
		}

	}

	public static class OrNode extends BaseNode {
		private Boolean result = null;

		@Override
		public boolean nodeIs(char c) {
			return c == '|';
		}

		public OrNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.next instanceof OrNode) {
							result = logicalGroupAndCompare(this, this.next);
							setPrev(this.prev.prev);
							setNext(this.next.next.next);
						} else if (this.prev instanceof OrNode) {
							result = logicalGroupAndCompare(this.prev, this);
							setPrev(this.prev.prev.prev);
							setNext(this.next.next);
						} else {
							Boolean left = Boolean.valueOf(this.prev.react().toString());
							Boolean right = Boolean.valueOf(this.next.react().toString());
							System.out.println(left + content.toString() + right);
							setPrev(this.prev.prev);
							setNext(this.next.next);
							result = left | right;
						}
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return result;
		}

		@Override
		public BaseNode multiply(char c) {
			return new OrNode(c);
		}

	}

	public static class XorNode extends BaseNode {
		private Boolean result = null;

		@Override
		public boolean nodeIs(char c) {
			return c == '^';
		}

		public XorNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						Boolean left = Boolean.valueOf(this.prev.react().toString());
						Boolean right = Boolean.valueOf(this.next.react().toString());
						System.out.println(left + content.toString() + right);
						setPrev(this.prev.prev);
						setNext(this.next.next);
						result = left ^ right;
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return result;
		}

		@Override
		public BaseNode multiply(char c) {
			return new XorNode(c);
		}

	}

	public static class NotNode extends BaseNode {
		private Boolean result = null;
		private Boolean quits = false;

		@Override
		public int getLevel() {
			if (this.next instanceof EqualNode) {
				return super.getLevel() - 1;
			}
			return super.getLevel();
		}

		@Override
		public boolean nodeIs(char c) {
			return c == '!';
		}

		public NotNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.next instanceof EqualNode && !this.next.reacted) {
							if (this.prev.react().toString().equals("true")
									|| this.prev.react().toString().equals("false")) {
								result = logicalGroupAndCompare(this, this.next);
							} else {
								int cmp = groupAndCompare(this, this.next);
								result = cmp != 0;
							}
							setPrev(this.prev.prev);
							setNext(this.next.next.next);
						} else {
							BaseNode tmp = this.prev;
							while (tmp instanceof NotNode) {
								this.content.insert(0, tmp.content);
								quits = !quits;
								destroy(tmp);
								tmp = tmp.prev;
							}
							tmp = this.next;
							while (tmp instanceof NotNode) {
								this.content.append(tmp.content);
								quits = !quits;
								destroy(tmp);
								tmp = tmp.next;
							}
							Boolean right = Boolean.valueOf(this.next.react().toString());
							if (!quits) {
								result = !right;
							} else {
								result = right;
							}
							System.out.println(content.toString() + right);
							setNext(this.next.next);
						}
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return result;
		}

		@Override
		public BaseNode multiply(char c) {
			return new NotNode(c);
		}

	}

	public static class EqualNode extends BaseNode {
		private Boolean result = null;

		@Override
		public boolean nodeIs(char c) {
			return c == '=';
		}

		public EqualNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.prev instanceof EqualNode) {
							if (this.next.react().toString().equals("true")
									|| this.next.react().toString().equals("false")) {
								result = logicalGroupAndCompare(this.prev, this);
							} else {
								int cmp = groupAndCompare(this.prev, this);
								result = cmp == 0;
							}
							setPrev(this.prev.prev.prev);
							setNext(this.next.next);
						} else if (this.next instanceof EqualNode) {
							if (this.next.react().toString().equals("true")
									|| this.next.react().toString().equals("false")) {
								result = logicalGroupAndCompare(this, this.next);
							} else {
								int cmp = groupAndCompare(this, this.next);
								result = cmp == 0;
							}
							setPrev(this.prev.prev);
							setNext(this.next.next.next);
						} else if (this.prev instanceof GreaterNode) {
							int cmp = groupAndCompare(this.prev, this);
							setPrev(this.prev.prev.prev);
							setNext(this.next.next);
							result = cmp >= 0;
						} else if (this.prev instanceof LessNode) {
							int cmp = groupAndCompare(this.prev, this);
							setPrev(this.prev.prev.prev);
							setNext(this.next.next);
							result = cmp <= 0;
						} else if (this.prev instanceof NotNode) {
							System.out.println("this.next.react()==" + this.next.react());
							if (this.next.react().toString().equals("true")
									|| this.next.react().toString().equals("false")) {
								result = logicalGroupAndCompare(this.prev, this);
							} else {
								int cmp = groupAndCompare(this.prev, this);
								result = cmp != 0;
							}
							setPrev(this.prev.prev.prev);
							setNext(this.next.next);
						}
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return result;
		}

		@Override
		public BaseNode multiply(char c) {
			return new EqualNode(c);
		}
	}

	public static class GreaterNode extends BaseNode {
		private Boolean result = null;

		@Override
		public boolean nodeIs(char c) {
			return c == '>';
		}

		public GreaterNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.next instanceof EqualNode) {
							int cmp = groupAndCompare(this.prev, this);
							setPrev(this.prev.prev);
							setNext(this.next.next.next);
							result = cmp >= 0;
						} else {
							int cmp = compare(this.prev.react().toString(), this.next.react().toString());
							setPrev(this.prev.prev);
							setNext(this.next.next);
							result = cmp > 0;
						}
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return result;
		}

		@Override
		public BaseNode multiply(char c) {
			return new GreaterNode(c);
		}

	}

	public static class LessNode extends BaseNode {
		private Boolean result = null;

		@Override
		public boolean nodeIs(char c) {
			return c == '<';
		}

		public LessNode(char c) {
			content.append(c);
		}

		@Override
		public Object react() {
			if (!this.reacted)
				synchronized (this.reacted) {
					if (!this.reacted) {
						this.reacted = true;
						if (this.next instanceof EqualNode) {
							int cmp = groupAndCompare(this.prev, this);
							setPrev(this.prev.prev);
							setNext(this.next.next.next);
							result = cmp <= 0;
						} else {
							int cmp = compare(this.prev.react().toString(), this.next.react().toString());
							setPrev(this.prev.prev);
							setNext(this.next.next);
							result = cmp < 0;
						}
						Object upObject = up(this, false);
						if (upObject != null) {
							return upObject;
						}
					}
				}
			return result;
		}

		@Override
		public BaseNode multiply(char c) {
			return new LessNode(c);
		}

	}

	public static class BracketNode extends BaseNode {

		private static final String brackets = "()";
		private int index = -1;
		private boolean begin = false;

		@Override
		public boolean nodeIs(char c) {
			return brackets.indexOf(c) >= 0;
		}

		public boolean isBegin(char c) {
			return brackets.indexOf(c) % 2 == 0;
		}

		public boolean isBegin() {
			return begin;
		}

		@Override
		public Object react() {
			if (!this.reacted)
				if (!this.reacted) {
					Object result = null;
					BaseNode contentNode = null;
					if (this.begin) {
						result = this.next.react();
						if (this.next.next instanceof BracketNode && !((BracketNode) this.next.next).begin) {
							this.reacted = true;
							this.next.next.reacted = true;
							destroy(this);
							destroy(this.next.next);
							contentNode = this.next;
						}
					} else {
						result = this.prev.react();
						if (this.prev.prev instanceof BracketNode && !((BracketNode) this.prev.prev).begin) {
							this.reacted = true;
							this.prev.prev.reacted = true;
							destroy(this);
							destroy(this.prev.prev);
							contentNode = this.prev;
						}
					}
					Object upObject = up(contentNode, false);
					if (upObject != null) {
						return upObject;
					}
					return result;
				}
			return StringUtils.EMPTY;
		}

		public BracketNode(char c) {
			this.index = brackets.indexOf(c);
			this.begin = index % 2 == 0;
			content.append(c);
		}

		@Override
		public BaseNode multiply(char c) {
			return new BracketNode(c);
		}

	}

	public static void addNode(BaseNode ancestor) {
		addNode(orders.size(), ancestor, false);
	}

	public static void addNode(int index, BaseNode ancestor) {
		addNode(index, ancestor, false);
	}

	@SuppressWarnings("unchecked")
	public static void addNode(int index, BaseNode ancestor, boolean parallel) {
		if (parallel) {
			if (index >= orders.size()) {
				orders.add(index, ancestor);
			} else {
				Object node = orders.get(index);
				if (node == null) {
					List<BaseNode> list = new ArrayList<>();
					list.add(ancestor);
					orders.set(index, ancestor);

				} else if (node instanceof List) {
					List<BaseNode> list = (List<BaseNode>) node;
					list.add(ancestor);
				} else if (node instanceof BaseNode) {
					List<BaseNode> list = new ArrayList<>();
					list.add((BaseNode) node);
					list.add(ancestor);
					orders.set(index, list);
				}
			}
		} else {
			orders.add(index, ancestor);
		}
	}

	public static Object parseComplexExpression(String expression) {
		Pattern p = Pattern.compile(EXPRESSION_FINDER);
		Matcher m = p.matcher(expression);
		StringBuffer result = new StringBuffer();
		while (m.find()) {
			result.append(parseExpression(m.group(1)));
			result.append(m.group(2));
		}
		return result.toString();
	}

	public static Object parseExpression(String expression) {
		if (StringUtils.isEmpty(expression)) {
			throw new IllegalArgumentException("invalid parameter " + expression);
		}
		BaseNode node = null, prev = null;
		for (int i = 0; i < expression.length(); i++) {
			char c = expression.charAt(i);
			node = switchNodes(c, prev);
			if (node == null) {

			}
			if (prev != null) {
				node.setPrev(prev);
				prev.setNext(node);
			}
			prev = node;
		}
		return node == null ? null : node.react();
	}

	@SuppressWarnings({ "unchecked" })
	private static BaseNode switchNodes(char c, BaseNode prev) {
		for (Object order : orders) {
			if (order instanceof List) {
				List<BaseNode> nodes = (List<BaseNode>) order;
				for (BaseNode node : nodes) {
					node.setPrev(prev);
					if (node.nodeIs(c)) {
						return node.multiply(c);
					}
				}
			} else {
				BaseNode node = (BaseNode) order;
				node.setPrev(prev);
				if (node.nodeIs(c)) {
					return node.multiply(c);
				}
			}
		}
		return null;
	}
}
