package xyz.ipurple.wechat.base.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ipurple.wechat.base.constants.Constants;
import xyz.ipurple.wechat.base.constants.WechatMsgConstants;
import xyz.ipurple.wechat.base.core.WechatInfo;
import xyz.ipurple.wechat.base.core.init.ContactEntity;
import xyz.ipurple.wechat.base.core.init.WechatInitEntity;
import xyz.ipurple.wechat.base.core.sync.SyncEntity;
import xyz.ipurple.wechat.login.UserContext;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;

/**
 * @ClassName: WechatHelper
 * @Description: 微信工具类
 * @Author: zcy
 * @Date: 2018/8/6 10:32
 * @Version: 1.0
 */
public class WechatHelper {
    private static final Logger logger = LoggerFactory.getLogger(WechatHelper.class);

    private static QRCodeWindow QR_CODE_WINDOW = null;

    /**
     * 获取二维码登陆uuid
     *
     * @return
     */
    public static String getQrLoginUUID() {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("appid", "wx782c26e4c19acffb"));
        params.add(new BasicNameValuePair("redirect_uri", "https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage"));
        params.add(new BasicNameValuePair("fun", "new"));
        params.add(new BasicNameValuePair("lang", "zh_CN"));
        params.add(new BasicNameValuePair("_", System.currentTimeMillis() + ""));
        HttpResponse httpResponse = HttpClientHelper.build(Constants.JS_LOGIN_URL).setParams(params).doPost();
        String result = httpResponse.getContent();
        if (StringUtils.isBlank(result)) {
            throw new RuntimeException("获取UUID失败");
        }
        String code = MatcheHelper.matches("window.QRLogin.code = (\\d+);", result);
        if (null == code || !code.equals("200")) {
            throw new RuntimeException("获取uuid失败," + code);
        }
        return MatcheHelper.matches("window.QRLogin.uuid = \"(.*)\";", result);
    }

    public static String getQrCode(String uuid) {
        if (StringUtils.isBlank(uuid)) {
            throw new RuntimeException("uuid不存在");
        }
        String url = Constants.LOGIN_QRCODE + uuid;
        String path = Constants.QRCODE_TEMP_DIR;
        String fileName = uuid + ".jpg";
        HttpClientHelper.build(url).doPostFile(Constants.QRCODE_TEMP_DIR, fileName);
        return path + File.separator + fileName;
    }

    public static void showQrCode(final String path) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                    QRCodeWindow qrCodeWindow = new QRCodeWindow(path);
                    QR_CODE_WINDOW = qrCodeWindow;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void closeQrCode() {
        QR_CODE_WINDOW.dispose();
    }

    public static void deleteQrCode(String qrCodePath) {
        File file = new File(qrCodePath);
        if (file.exists()) {
            file.delete();
        }
    }

    public static String waitLogin(int tip, String uuid) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("loginicon", "true"));
        params.add(new BasicNameValuePair("uuid", uuid));
        params.add(new BasicNameValuePair("tip", tip + ""));
        params.add(new BasicNameValuePair("_", System.currentTimeMillis() + ""));
        HttpResponse httpResponse = HttpClientHelper.build(Constants.WAIT_LOGIN).setParams(params).doPost();
        return httpResponse.getContent();
    }

    /**
     * 扫码后跳转链接并初始化基础信息
     *
     * @param res
     */
    public static WechatInfo redirect(String res) {
        String redirectUri = MatcheHelper.matches("window.redirect_uri=\"(.*)\"", res);
        HttpResponse httpResponse = HttpClientHelper.build(redirectUri + "&fun=new").doPost();
        String content = httpResponse.getContent();
        String retCode = MatcheHelper.matches("<ret>(.*)</ret>", content);
        if (Constants.NEW_WECHAT_CAN_NOT_LOGIN.equals(retCode)) {
            throw new RuntimeException("2018年后注册的微信账号不能登陆");
        }

        String skey = MatcheHelper.matches("<skey>(.*)</skey>", content);
        String wxsid = MatcheHelper.matches("<wxsid>(.*)</wxsid>", content);
        String wxuin = MatcheHelper.matches("<wxuin>(.*)</wxuin>", content);
        String passTicket = MatcheHelper.matches("<pass_ticket>(.*)</pass_ticket>", content);

        //获取基础url
        String baseUrl = redirectUri.substring(0, redirectUri.lastIndexOf("/"));

        WechatInfo info = new WechatInfo();
        info.setCookie(httpResponse.getCookie());
        info.setPassicket(passTicket);
        info.setSkey(skey);
        info.setWxsid(wxsid);
        info.setWxuin(wxuin);
        info.setBaseUrl(baseUrl);

        JSONObject json = new JSONObject();
        json.put("DeviceID", info.getDeviceId());
        json.put("Sid", info.getWxsid());
        json.put("Skey", info.getSkey());
        json.put("Uin", info.getWxuin());
        info.setBaseRequest(json);
        return info;
    }

    public static void init(WechatInfo wechatInfo) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("lang", "zh_CN"));
        params.add(new BasicNameValuePair("pass_ticket", wechatInfo.getPassicket()));
        params.add(new BasicNameValuePair("r", System.currentTimeMillis() + ""));

        JSONObject payLoad = new JSONObject();
        payLoad.put("BaseRequest", wechatInfo.getBaseRequest());
        HttpResponse httpResponse = HttpClientHelper.build(wechatInfo.getBaseUrl() + Constants.INIT_URL, wechatInfo.getCookie()).setPayLoad(payLoad.toJSONString()).doPost();
        String content = httpResponse.getContent();
        WechatInitEntity wechatInitEntity = JSON.parseObject(content, WechatInitEntity.class);
        //获取登陆人信息
        wechatInfo.setUser(wechatInitEntity.getUser());
        //获取sync 字符串型
        wechatInfo.setSyncKeyStr(createSyncKey(wechatInitEntity.getSyncKey()));
        wechatInfo.setSyncKey(wechatInitEntity.getSyncKey());

        Iterator<ContactEntity> iterator = wechatInitEntity.getContactList().iterator();
        while (iterator.hasNext()) {
            ContactEntity next = iterator.next();
            if (Constants.RECEIVE_MSG_FLAG) {
                logger.info(next.getUserName() + "  ||  " + next.getNickName());
            }
        }
    }

    public static String getIcon(WechatInfo wechatInfo, String userName) {
        StringBuffer url = new StringBuffer(wechatInfo.getBaseUrl() + Constants.GET_ICON);
        url.append("?seq=").append(System.currentTimeMillis())
                .append("&username=").append(userName)
                .append("&skey=").append(wechatInfo.getSkey());
        HttpClientHelper.build(url.toString(), wechatInfo.getCookie()).doPostFile(Constants.QRCODE_TEMP_DIR, userName+".jpg");
        String filePath = Constants.QRCODE_TEMP_DIR + userName + ".jpg";
        File image = new File(filePath);
        if (image.exists()) {
            String imageBase64 = FileUtil.getImageBase64(filePath);
            image.delete();
            return imageBase64;
        }
        return null;
    }

    public static void statusNotify(WechatInfo wechatInfo) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("lang", "zh_CN"));
        params.add(new BasicNameValuePair("pass_ticket", wechatInfo.getPassicket()));

        JSONObject payLoad = new JSONObject();
        payLoad.put("BaseRequest", wechatInfo.getBaseRequest());
        payLoad.put("ClientMsgId", System.currentTimeMillis());
        payLoad.put("Code", 3);
        payLoad.put("FromUserName", wechatInfo.getUser().getUserName());
        payLoad.put("ToUserName", wechatInfo.getUser().getUserName());

        HttpResponse httpResponse = HttpClientHelper.build(wechatInfo.getBaseUrl() + Constants.STATUS_NOTIFY_URL, wechatInfo.getCookie()).setPayLoad(payLoad.toJSONString()).doPost();
        JSONObject notifyResponse = JSON.parseObject(httpResponse.getContent());
        if (!((JSONObject) notifyResponse.get("BaseResponse")).get("Ret").equals(0)) {
            logger.error("wechat#" + Thread.currentThread().getId() + "--消息通知开启失败");
        } else {
            logger.info("wechat#" + Thread.currentThread().getId() + "--消息通知开启");
        }
    }

    public static String getContact(WechatInfo wechatInfo) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("skey", wechatInfo.getSkey()));
        params.add(new BasicNameValuePair("r", System.currentTimeMillis() + ""));
        params.add(new BasicNameValuePair("pass_ticket", wechatInfo.getPassicket()));

        HttpResponse httpResponse = HttpClientHelper.build(wechatInfo.getBaseUrl() + Constants.GET_CONTACT_URL, wechatInfo.getCookie()).setParams(params).doPost();
        JSONObject contact = JSONObject.parseObject(httpResponse.getContent());
        String ret = contact.getJSONObject("BaseResponse").getString("Ret");
        if (!ret.equals("0")) {
            logger.info("wechat#" + Thread.currentThread().getId() + "--获取联系人失败");
        }
        Iterator<JSONObject> memberListIt = contact.getObject("MemberList", List.class).iterator();
        while (memberListIt.hasNext()) {
            ContactEntity next = JSON.parseObject(memberListIt.next().toString(), ContactEntity.class);
            Map<String, ContactEntity> contactHashMap = UserContext.getContactThreadLocal();
            contactHashMap.put(next.getUserName(), next);
        }
        return httpResponse.getContent();
    }

    /**
     * 发送文本消息
     * @param content 发送内容
     * @param toUserName 接收人username
     */
    public static void sendTextMsg(String content, String toUserName) {
        WechatInfo wechatInfo = UserContext.getWechatInfoThreadLocal();
        String clientLocalId = System.currentTimeMillis()+ "" + (int)((Math.random() * 9 + 1) * 1000);

        JSONObject msg = new JSONObject();
        msg.put("ClientMsgId", clientLocalId);
        msg.put("LocalID", clientLocalId);
        msg.put("Content", content);
        msg.put("FromUserName", wechatInfo.getUser().getUserName());
        msg.put("Type", WechatMsgConstants.TEXT_MSG);
        msg.put("ToUserName", toUserName);

        JSONObject payLoad = new JSONObject();
        payLoad.put("BaseRequest", wechatInfo.getBaseRequest());
        payLoad.put("Msg", msg);
        payLoad.put("Scene", 0);

        String url = wechatInfo.getBaseUrl() + Constants.SEND_MSG_URL+ "?pass_ticket=" + wechatInfo.getPassicket();
        HttpResponse httpResponse = HttpClientHelper.build(url, wechatInfo.getCookie()).setPayLoad(payLoad.toJSONString()).doPost();
        JSONObject response = JSONObject.parseObject(httpResponse.getContent());
        if (!response.getJSONObject("BaseResponse").getString("Ret").equals("0")) {
            logger.info("wechat#" + Thread.currentThread().getId() + "--发送撤回消息失败");
        } else {
            logger.info("wechat#" + Thread.currentThread().getId() + "--发送撤回消息成功");
        }
    }

    /**
     * 发送图片文件消息
     * @param content 发送内容
     * @param toUserName 接收人username
     */
    public static void sendImageFileMsg(String content, String toUserName) {
        WechatInfo wechatInfo = UserContext.getWechatInfoThreadLocal();
        String clientLocalId = System.currentTimeMillis()+ "" + (int)((Math.random() * 9 + 1) * 1000);

        JSONObject msg = new JSONObject();
        msg.put("ClientMsgId", clientLocalId);
        msg.put("LocalID", clientLocalId);
        msg.put("Content", content);
        msg.put("FromUserName", wechatInfo.getUser().getUserName());
        msg.put("ToUserName", toUserName);
        msg.put("MediaId", "");
        msg.put("Type", WechatMsgConstants.IMAGE_FILE_MSG);

        JSONObject payLoad = new JSONObject();
        payLoad.put("BaseRequest", wechatInfo.getBaseRequest());
        payLoad.put("Msg", msg);
        payLoad.put("Scene", 2);

        String url = wechatInfo.getBaseUrl() + Constants.SEND_MSG_IMG_URL+ "?pass_ticket=" + wechatInfo.getPassicket()+"&fun=async&f=json";
        HttpResponse httpResponse = HttpClientHelper.build(url, wechatInfo.getCookie()).setPayLoad(payLoad.toJSONString()).doPost();
        JSONObject response = JSONObject.parseObject(httpResponse.getContent());
        if (!response.getJSONObject("BaseResponse").getString("Ret").equals("0")) {
            logger.info("wechat#" + Thread.currentThread().getId() + "--发送撤回图片文件失败");
        } else {
            logger.info("wechat#" + Thread.currentThread().getId() + "--发送撤回图片文件成功");
        }
    }

    /**
     * 发送图片表情
     * @param emoticonMd5 表情MD5值
     * @param toUserName 接收人username
     */
    public static void sendEmoticonMsg(String emoticonMd5, String toUserName) {
        WechatInfo wechatInfo = UserContext.getWechatInfoThreadLocal();
        String clientLocalId = System.currentTimeMillis()+ "" + (int)((Math.random() * 9 + 1) * 1000);

        JSONObject msg = new JSONObject();
        msg.put("ClientMsgId", clientLocalId);
        msg.put("LocalID", clientLocalId);
        msg.put("EMoticonMd5", emoticonMd5);
        msg.put("FromUserName", wechatInfo.getUser().getUserName());
        msg.put("ToUserName", toUserName);
        msg.put("Type", WechatMsgConstants.IMAGE_EXPRESSION_MSG);

        JSONObject payLoad = new JSONObject();
        payLoad.put("BaseRequest", wechatInfo.getBaseRequest());
        payLoad.put("Msg", msg);
        payLoad.put("Scene", 2);

        String url = wechatInfo.getBaseUrl() + Constants.SEND_EMOTICON_URL+ "?pass_ticket=" + wechatInfo.getPassicket()+"&fun=sys&lang=zh_CN";
        HttpResponse httpResponse = HttpClientHelper.build(url, wechatInfo.getCookie()).setPayLoad(payLoad.toJSONString()).doPost();
        JSONObject response = JSONObject.parseObject(httpResponse.getContent());
        if (!response.getJSONObject("BaseResponse").getString("Ret").equals("0")) {
            logger.info("wechat#" + Thread.currentThread().getId() + "--发送撤回图片表情失败");
        } else {
            logger.info("wechat#" + Thread.currentThread().getId() + "--发送撤回图片表情成功");
        }
    }

    /**
     * 获取图片
     * @param msgId
     * @return 文件存放路径
     */
    public static String getMsgImg(Long msgId) {
        WechatInfo wechatInfo = UserContext.getWechatInfoThreadLocal();
        StringBuffer url = new StringBuffer(wechatInfo.getBaseUrl() + Constants.GET_MSG_IMG_URL)
                        .append("?MsgID=")
                        .append(msgId)
                        .append("&skey=")
                        .append(wechatInfo.getSkey())
                        .append("&type=slave");
        String path = Constants.QRCODE_TEMP_DIR;
        String fileName = msgId + ".jpg";
        HttpClientHelper.build(url.toString(), wechatInfo.getCookie()).doPostFile(Constants.QRCODE_TEMP_DIR, msgId + ".jpg");
        return path + File.separator + fileName;
    }

    /**
     * 检查是否有最新消息
     * @param wechatInfo
     * @return
     */
    public static int[] syncCheck(WechatInfo wechatInfo) {
        return syncCheck(wechatInfo, null);
    }

    /**
     * 检查是否有最新消息
     * @param wechatInfo
     * @return
     */
    public static int[] syncCheck(WechatInfo wechatInfo, String url) {
        url = null == url ? wechatInfo.getSyncURL() : url;

        StringBuffer urlParams = new StringBuffer();
        urlParams.append("?r=").append(System.currentTimeMillis())
                .append("&skey=").append(URLEncoder.encode(wechatInfo.getSkey()))
                .append("&sid=").append(URLEncoder.encode(wechatInfo.getWxsid()))
                .append("&uin=").append(URLEncoder.encode(wechatInfo.getWxuin()))
                .append("&deviceid=").append(URLEncoder.encode(wechatInfo.getDeviceId()))
                .append("&synckey=").append(URLEncoder.encode(wechatInfo.getSyncKeyStr()))
                .append("&_=").append(System.currentTimeMillis());

        HttpResponse httpResponse = HttpClientHelper.build(url + urlParams.toString(), wechatInfo.getCookie()).doGet();
        JSONObject syncCheck = JSON.parseObject(httpResponse.getContent().split("=")[1]);
        int retcode = syncCheck.getIntValue("retcode");
        int selector = syncCheck.getIntValue("selector");
        int result[] = {retcode, selector};
        return result;
    }

    /**
     * 获取最新消息
     * @param wechatInfo
     * @return
     * @throws UnsupportedEncodingException
     */
    public static SyncEntity getTextMsg(WechatInfo wechatInfo) throws UnsupportedEncodingException {
        JSONObject payLoad = new JSONObject();
        payLoad.put("BaseRequest", wechatInfo.getBaseRequest());
        payLoad.put("SyncKey", wechatInfo.getSyncKey());
        payLoad.put("rr", ~new Date().getTime());

        StringBuffer url = new StringBuffer(wechatInfo.getBaseUrl()+Constants.SYNC_URL);
        url.append("?sid=").append(URLEncoder.encode(wechatInfo.getWxsid(),"utf-8"))
                .append("&skey=").append(URLEncoder.encode(wechatInfo.getSkey(),"utf-8"))
                .append("&lang=").append("zh_CN")
                .append("&pass_ticket=").append(wechatInfo.getPassicket());

        HttpResponse httpResponse = HttpClientHelper.build(url.toString(), wechatInfo.getCookie()).setPayLoad(payLoad.toJSONString()).doPost();
        SyncEntity syncEntity = JSON.parseObject(httpResponse.getContent(), SyncEntity.class);
        //更新synckey
        wechatInfo.setSyncKey(syncEntity.getSyncKey());
        wechatInfo.setSyncKeyStr(WechatHelper.createSyncKey(syncEntity.getSyncKey()));
        int ret = syncEntity.getBaseResponse().getRet();
        if (ret != 0) {
            throw new RuntimeException("获取最新消息失败");
        }
        return syncEntity;
    }

    public static String createSyncKey(JSONObject syncKey) {
        StringBuffer sb = new StringBuffer();
        Iterator<JSONObject> syncKeyIt = ((List<JSONObject>) syncKey.get("List")).iterator();
        while (syncKeyIt.hasNext()) {
            JSONObject next = syncKeyIt.next();
            sb.append("|")
                    .append(next.getString("Key"))
                    .append("_")
                    .append(next.getString("Val"));
        }
        return sb.substring(1);
    }

    /**
     * 选择线路
     * @param wechatInfo
     */
    public static void chooseSyncLine(WechatInfo wechatInfo) {
        for (String syncHost : Constants.SYNC_HOST) {
            String syncURL = new StringBuilder("https://")
                    .append(syncHost)
                    .append("/cgi-bin/mmwebwx-bin")
                    .append(Constants.SYNC_CHECK_URL).toString();
            int[] result = syncCheck(wechatInfo, syncURL);
            if (0 == result[0]) {
                wechatInfo.setSyncURL(syncURL);
                logger.info("wechat#" + Thread.currentThread().getId() + "--选择同步线路{}", syncURL);
                break;
            }
        }
        if (StringUtils.isBlank(wechatInfo.getSyncURL())) {
            throw new RuntimeException("没有找到合适的线路");
        }
    }
}
