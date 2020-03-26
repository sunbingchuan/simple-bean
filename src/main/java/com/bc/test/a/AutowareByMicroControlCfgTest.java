package com.bc.test.a;

import com.bc.simple.bean.common.annotation.Bean;
import com.bc.simple.bean.common.annotation.Configuration;
import com.bc.simple.bean.common.annotation.Order;

@Configuration
public class AutowareByMicroControlCfgTest {
	@Bean
	public AutowareByMicroControlTest get() {
		return new AutowareByMicroControlTest();
	}
	
	@Bean
	public AutowareByMicroControlTest getOther(AutowareByMicroControlTest test) {
		System.out.println("old get "+test);
		return new AutowareByMicroControlTest();
	}
	@Bean
	@Order(-1)
	public FiveBean five() {
		FiveBean five= new FiveBean();
		five.flag="123";
		return five;
	}
}
