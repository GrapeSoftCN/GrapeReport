package test;

import httpServer.booter;

public class testwechat {
	public static void main(String[] args) {
		 booter booter = new booter();
		 try {
		 System.out.println("GrapeReport!");
		 System.setProperty("AppName", "GrapeReport");
		 booter.start(1003);
		 } catch (Exception e) {
		 }
		// String string =
		// "{\"name\":\"test\",\"phone\":\"13356897845\",\"IDCard\":\"340721199211262414\",\"openid\":\"oZU2Lw7s_7bATZXXJL5L2CvmFrCY\"}";
		// System.out.println(new Report().Certification(string));
//		new Report().TimerSendCount(
//				"{\"day\":\"1\",\"hour\":\"1\",\"phone\":\"18756282651\"}");
	}
}
