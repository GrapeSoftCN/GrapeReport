package interfaceApplication;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import esayhelper.JSONHelper;
import esayhelper.TimeHelper;
import model.ReportModel;
import nlogger.nlogger;
import security.codec;
import sms.ruoyaMASDB;

public class Report {
	private static boolean initThread;
	private ReportModel model = new ReportModel();
	private HashMap<String, Object> map = new HashMap<>();
	private ScheduledExecutorService service = Executors
			.newSingleThreadScheduledExecutor();
	static{
		initThread = false;
	}
	public Report() {
		map.put("time", String.valueOf(TimeHelper.nowMillis()));
		map.put("state", 0); // 0：已受理；1：处理中；2：已处理；3：被拒绝
		map.put("type", 0);
		map.put("isdelete", 0);
		map.put("mode", 0);
		map.put("reason", "");
		map.put("handletime", "");
		map.put("completetime", "");
		map.put("refusetime", "");
		map.put("Rgroup", "");
	}

	// 新增
	@SuppressWarnings("unchecked")
	public String AddReport(String ReportInfo) {
		JSONObject object = model.AddMap(map,
				JSONHelper.string2json(ReportInfo));
		object.put("content",
				codec.encodebase64(object.get("content").toString()));
		return model.Add(object.toString());
	}

	public String insert(String info) {
		return model.insert(JSONHelper.string2json(info));
	}

	// 修改
	public String UpdateReport(String id, String typeInfo) {
		JSONObject object = JSONHelper.string2json(typeInfo);
		return model.resultMessage(model.Update(id, object), "修改成功");
	}

	// 删除
	public String DeleteReport(String id) {
		return model.resultMessage(model.Delete(id), "修改成功");
	}

	// 批量删除
	public String DeleteBatchReport(String ids) {
		return model.resultMessage(model.Delete(ids.split(",")), "删除成功");
	}

	// 分页
	public String PageReport(int idx, int pageSize) {
		return model.page(idx, pageSize);
	}

	// 模糊查询
	public String PageByReport(int ids, int pageSize, String info) {
		return model.page(ids, pageSize, JSONHelper.string2json(info));
	}

	// 批量查询
	public String BatchSelect(String info, int no) {
		return model.Select(JSONHelper.string2json(info), no);
	}

	// 条件查询，默认查询正在处理的举报件
	public String find(String info) {
		return model.finds(JSONHelper.string2json(info));
	}

	@SuppressWarnings("unchecked")
	public String excel(String info) {
		JSONObject object = new JSONObject();
		JSONArray array = model.findexcel(JSONHelper.string2json(info));
		for (int i = 0; i < array.size(); i++) {
			object = (JSONObject) array.get(i);
			for (Object obj : object.keySet()) {
				String value = object.get(obj.toString()).toString();
				if (value.contains("$numberLong")) {
					JSONObject object2 = JSONHelper.string2json(value);
					object.put(obj.toString(), Long.parseLong(object2.get("$numberLong").toString()));
				}
			}
		}
		return object.toString();
	}
	// 举报件处理完成
	public String CompleteReport(String id, String reson) {
		return model.resultMessage(
				model.Complete(id, JSONHelper.string2json(reson)), "举报件已处理完成");
	}

	// 举报拒绝
	public String RefuseReport(String id, String reson) {
		return model.resultMessage(
				model.Refuse(id, JSONHelper.string2json(reson)),
				"举报情况不属实，已被拒绝处理");
	}

	// 举报件正在处理
	@SuppressWarnings("unchecked")
	public String HandleReport(String id, String typeInfo) {
		JSONObject object = JSONHelper.string2json(typeInfo);
		if (!object.containsKey("handletime")) {
			object.put("handletime", String.valueOf(TimeHelper.nowMillis()));
		}
		if (!object.containsKey("state")) {
			object.put("state", 2);
		}
		// 添加操作日志
//		JSONObject objects = new JSONObject();
		// object.put("OperateId", object.get("").toString());
//		object.put("ReportId", id);
//		object.put("time", TimeHelper.nowMillis());
//		object.put("ContentLog", "");
//		object.put("step", "该举报件已处理完结");
//		appsProxy.proxyCall(
//				"123.57.214.226:801", appsProxy.appid()
//						+ "/45/ReportLog/Addlog/" + objects.toString(),
//				null, "");
		return model.resultMessage(model.Update(id, object), "举报件正在处理");
	}

	// 合并举报件【ids之间使用","隔开】
	public String JoinReport(String ids) {
		return model.resultMessage(model.ReportJoin(ids.split(",")), "合并举报件成功");
	}

	// 查询个人相关的举报件
	public String searchById(String userid, int no) {
		return model.search(userid, no);
	}

	public String FeedCount(String userid) {
		return model.feed(userid);
	}

	// 查询个人相关的举报件的总数
	public String CountById(String userid) {
		return model.counts(userid);
	}

	// 模糊查询
	public String search(int ids, int pageSize, String info) {
		return model.find(ids, pageSize, JSONHelper.string2json(info));
	}

	// 导出举报件
	public String Export(String info) {
		return model.print(info);
	}

	public String findById(String id) {
		return model.SearchById(id);
	}

	// 恢复当前操作
	public String resume(String info) {
		return model.resu(JSONHelper.string2json(info));
	}

	// 验证内容是否含有敏感字符串
	public String checkContent(String content) {
		return model.resultMessage(model.checkCont(content), "不含敏感字符串");
	}

	// 获取用户openid，实名认证
	public String getUserId(String code, String url) {
		return model.getId(code, url);
	}

	// 实名认证
	public String Certification(String info) {
		return model.resultMessage(model.Certify(info), "验证码发送成功");
	}

	// 定时任务，定时删除被拒绝的举报件（每5天删除被拒绝的任务）
	public String TimerDelete() {
		int delay = 0;
		int period = 5;
		service.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				model.Delete();
			}
		}, delay, period, TimeUnit.DAYS);
		return "currentTime:" + new Date();
	}

	// 对用户进行封号
	@SuppressWarnings("unchecked")
	public String kick(String openid,String info) {
		String code = "0";
		JSONObject object = JSONHelper.string2json(info);
		String message = model.UserKick(openid, object);
		if (("0").equals(
				JSONHelper.string2json(message).get("errorcode").toString())) {
			if (object.containsKey("reason")) {
				JSONObject obj = new JSONObject();
				obj.put("reason", object.get("reason").toString());
				String messages = RefuseReport(object.get("_id").toString(),
						obj.toString());
				code = String.valueOf(
						JSONHelper.string2json(messages).get("errorcode"));
			}
			return model.resultMessage(Integer.parseInt(code), "操作成功");
		}
		return model.resultMessage(18,"");
	}

	// 定时解封
	public String Unkick(String openid, String info) {
		int delay = 0;
		int period = 1;
		service.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				model.UserUnKick();
			}
		}, delay, period, TimeUnit.DAYS);
		return "currentTime:" + new Date();
	}

	// 尚未被处理的事件总数
	public String Count() {
		return model.counts();
	}

	// 统计量
	public String ReportCount(String param) {
		return model.EventCounts(JSONHelper.string2json(param));
	}

	// 统计量与全量比例
	public String ReportPercent(String param) {
		return model.PercentCounts(JSONHelper.string2json(param));
	}

	// 定时发送增加量到管理员手机号
	public String TimerSendCount(String param) {
		JSONObject object = JSONHelper.string2json(param);
		Calendar date = Calendar.getInstance();
		int hour = 8, day = 1;
		if (object.containsKey("hour")) {
			if (!("").equals(object.get("hour").toString())) {
				hour = Integer.parseInt(object.get("hour").toString());
			}
		}
		if (object.containsKey("day")) {
			if (!("").equals(object.get("day").toString())) {
				day = Integer.parseInt(object.get("day").toString());
			}
		}
		date.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH),
				date.get(Calendar.DATE), hour, 0, 0);
		if( initThread == false ){
			initThread = true;
			JSONObject jObject = appsProxy.configValue();
			service.scheduleAtFixedRate( ()->{
				if (object.containsKey("phone")) {
					int count = Integer.parseInt(model.TimerInsertCount(jObject));
					if (count>0) {
						ruoyaMASDB.sendSMS(
								object.get("phone").toString(),
								"24小时内，新增举报量为："+String.valueOf(count));
					}else{
						nlogger.logout(".....");
					}
				} else {
					nlogger.logout("没有手机号，无法发送短信至管理员");
				}
			}, 2, day, TimeUnit.SECONDS);
		}
		return model.resultMessage(0, "任务执行成功");
	}
}
