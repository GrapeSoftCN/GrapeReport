package test;

import httpServer.booter;
import interfaceApplication.Report;

public class testReport {

	public static void main(String[] args) {
		booter booter = new booter();
		 try {
		 System.out.println("GrapeReport!");
		 System.setProperty("AppName", "GrapeReport");
		 booter.start(1003);
		} catch (Exception e) {
		}
//		System.out.println(new Report().findById("591a78241a4769cbf58753ea"));
//		System.out.println(new Report().search(1, 2, "{\"state\":0}"));
	}
	

}
