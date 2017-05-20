package interfaceApplication;

import java.util.Date;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONObject;

import esayhelper.JSONHelper;
import esayhelper.TimeHelper;
import model.ReportModel;

public class Report {
	private ReportModel model = new ReportModel();
	private HashMap<String, Object> map = new HashMap<>();
	private ScheduledExecutorService service = Executors
			.newSingleThreadScheduledExecutor();

	public Report() {
		map.put("time", String.valueOf(TimeHelper.nowMillis()));
		map.put("state", 0); // 0：已受理；1：处理中；2：已处理；3：被拒绝
		map.put("type", 0);
		map.put("isdelete", 0);
		map.put("mode", 0);
		map.put("reson", "");
		map.put("handletime", "");
		map.put("completetime", "");
		map.put("refusetime", "");
	}

	// 新增
	public String AddReport(String ReportInfo) {
		JSONObject object = model.AddMap(map,
				JSONHelper.string2json(ReportInfo));
		return model.Add(object);
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
		return model.resultMessage(model.Update(id, object), "举报件正在处理");
	}

	// 查询个人相关的举报件
	public String searchById(String userid, int no) {
		return model.search(userid, no);
	}

	// 查询个人相关的举报件的总数
	public String CountById(String userid) {
		return model.count(userid);
	}

	// 查询含有反馈信息
	public String search(int ids,int pageSize,String info) {
		return model.find(ids, pageSize, JSONHelper.string2json(info));
	}

	// 导出举报件
	public String printWord(String info) {
		return model.print(info);
	}

	public String findById(String id) {
		return model.SearchById(id);
	}

	// 恢复当前操作
	public String resume(String info) {
		return model.resultMessage(model.resu(JSONHelper.string2json(info)),
				"操作成功");
	}

	// 验证内容是否含有敏感字符串
	public String checkContent(String content) {
		return model.resultMessage(model.checkCont(content), "不含敏感字符串");
	}

	// 获取用户openid，实名认证
	public String getUserId(String code) {
		return model.getId(code);
	}

	// 实名认证
	public String Certification(String info) {
		return model.resultMessage(model.Certify(info),
				"验证码发送成功");
	}

	// 定时任务，定时删除被拒绝的举报件（每5天删除被拒绝的任务）
	public String TimerDelete() {
		int delay = 0;
		int period = 5;
		service.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				model.Delete();
				// nlogger.logout(model.Delete()!=0?"删除成功":"删除失败");
			}
		}, delay, period, TimeUnit.DAYS);
		return "start:" + new Date();
	}
}
