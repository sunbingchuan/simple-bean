package com.bc.test.a;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Resource;

import com.bc.simple.bean.common.annotation.Component;
import com.bc.simple.bean.common.util.StringUtils;

@Component
public class MutiTypeTest {

	@Resource
	List<String> list;

	@Resource
	Map<String, String> map;

	@Resource
	Stream<String> stream;

	@Resource
	Set<String> set;

	@Resource
	Collection<String> collection;

	@Resource
	String[] strs;

	public MutiTypeTest() {
		System.out.println("");
	}

	@Override
	public String toString() {
		return StringUtils.toString(this.list)+"\nmap"
				+ StringUtils.toString(this.map)+"\nstream"
				+ StringUtils.toString(this.stream)+"\nset"
				+ StringUtils.toString(this.set)+"\ncollection"
				+ StringUtils.toString(this.collection) + "\nstrs"
				+ StringUtils.toString(this.strs!=null?this.strs[0]:"")+"";
	}
}
