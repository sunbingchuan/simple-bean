package com.bc.test.a;

import javax.annotation.Resource;

import com.bc.simple.bean.common.annotation.Autowired;
import com.bc.simple.bean.common.annotation.Scope;
import com.bc.simple.bean.common.annotation.Service;
import com.bc.simple.bean.common.annotation.Value;
import com.bc.simple.bean.common.util.StringUtils;

@Service
@Scope()
public class AutowareByMicroControlTest {
	@Value("iim ddg")
	private String s;
	@Autowired
	private String m;
	@Resource(name = "makeSuperMan")
	private String n;
	public AutowareByMicroControlTest() {
		System.out.println("");
	}
	
	@Resource
	private FiveBean fiveBean;

	@Override
	public String toString() {
		return StringUtils.toString(s) + "\nm="
				+ m + "\nn=" + n + "\n"+fiveBean;
	}


}
