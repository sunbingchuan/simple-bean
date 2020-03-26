package com.bc.test.b;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;

public class CommonTest {
	public static void main(String[] args) throws IOException, URISyntaxException {
		Enumeration<URL> urls =ClassLoader.getSystemResources("META-INF/simple.handlers");
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			InputStream in = url.openConnection().getInputStream();
			byte[] bs =new byte[1024];
			int i=0;
			while ((i=in.read(bs))>0) {
				System.out.print(new String(bs, 0, i));
			}
			in.close();
//			Properties props=new Properties();
//			props.load(in);
//			System.out.println(props);
		}
		//System.out.println(ClassLoader.getSystemResource("META-INF/simple.handlers"));
	}
}
