package xyz.ipurple.wechat.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import xyz.ipurple.wechat.base.core.WechatInfo;
import xyz.ipurple.wechat.base.core.init.ContactEntity;
import xyz.ipurple.wechat.base.core.send.msg.SendMsgDto;
import xyz.ipurple.wechat.base.core.sync.SyncEntity;
import xyz.ipurple.wechat.base.core.sync.msg.MsgEntity;
import xyz.ipurple.wechat.base.util.*;
import xyz.ipurple.wechat.login.core.Login;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @ClassName: WechatListener
 * @Description: //TODO
 * @Author: zcy
 * @Date: 2018/8/8 12:43
 * @Version: 1.0
 */
public class WechatListener {
    private static final Logger logger = Logger.getLogger(WechatListener.class);

    public void listen() {
        WechatInfo wechatInfo = Login.WECHAT_INFO_THREAD_LOCAL.get();
        while (true) {
            try {
                logger.info("正在监听:");
//                wechatInfo.setDeviceId("e" + System.currentTimeMillis());
                String selector = syncCheck(wechatInfo);
                //TODO 对语音和图片消息做处理
                if (selector.equals("2")) {
                    SyncEntity syncEntity = getTextMsg(wechatInfo);
                    Iterator<MsgEntity> msgIt = syncEntity.getAddMsgList().iterator();
                    while (msgIt.hasNext()) {
                        MsgEntity next = msgIt.next();
                        if (next.getMsgType() == 10002) {
                            //获取撤回的消息，并发送给文件助手
                            logger.info("有撤回消息：" + next.toString());
                            getRevokeMsg(next);
                        }
                        logger.info(next.getContent());
                    }
                    continue;
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查是否有最新消息
     * @param wechatInfo
     * @return
     */
    public String syncCheck(WechatInfo wechatInfo) {
        StringBuffer url = new StringBuffer();
        url.append("?r=").append(System.currentTimeMillis())
                .append("&skey=").append(URLEncoder.encode(wechatInfo.getSkey()))
                .append("&sid=").append(URLEncoder.encode(wechatInfo.getWxsid()))
                .append("&uin=").append(URLEncoder.encode(wechatInfo.getWxuin()))
                .append("&deviceid=").append(URLEncoder.encode(wechatInfo.getDeviceId()))
                .append("&synckey=").append(URLEncoder.encode(wechatInfo.getSyncKeyStr()))
                .append("&_=").append(System.currentTimeMillis());

        HttpResponse httpResponse = HttpClientHelper.build(Constants.SYNC_CHECK_URL + url.toString(), wechatInfo.getCookie()).doGet();
        JSONObject syncCheck = JSON.parseObject(httpResponse.getContent().split("=")[1]);
        String retcode = syncCheck.get("retcode").toString();
        if (!retcode.equals("0")) {
            throw new RuntimeException("sync check 失败");
        }
        return syncCheck.get("selector").toString();
    }

    /**
     * 获取最新消息
     * @param wechatInfo
     * @return
     * @throws UnsupportedEncodingException
     */
    public SyncEntity getTextMsg(WechatInfo wechatInfo) throws UnsupportedEncodingException {
        JSONObject payLoad = new JSONObject();
        payLoad.put("BaseRequest", wechatInfo.getBaseRequest());
        payLoad.put("SyncKey", wechatInfo.getSyncKey());
        payLoad.put("rr", ~new Date().getTime());

        StringBuffer url = new StringBuffer(Constants.SYNC_URL);
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
        List<MsgEntity> addMsgList = syncEntity.getAddMsgList();
        if (!addMsgList.isEmpty()) {
            Login.getMsgThreadLocal().put(addMsgList.get(0).getMsgId(), addMsgList.get(0));
        }
        return syncEntity;
    }

    /**
     * 获取到撤回的消息并发送给当前用户的文件助手
     * @param msgEntity
     */
    private void getRevokeMsg(MsgEntity msgEntity) {
        String content = StringEscapeUtils.unescapeXml(msgEntity.getContent());
        //匹配撤回的消息id
        String revokeMsgid = MatcheHelper.matches("<msgid>(.*)</msgid>", content);
        String replaceMsg = MatcheHelper.matches("<replacemsg>(.*)</replacemsg>", content);
        //从消息列表中查找到撤回的消息
        MsgEntity oldMsg = Login.getMsgThreadLocal().get(Long.valueOf(revokeMsgid));
        //拼装消息
        StringBuffer revokeMsgContent = new StringBuffer("");
        if (Login.getContactThreadLocal().containsKey(oldMsg.getFromUserName())) {
            ContactEntity contactEntity = Login.getContactThreadLocal().get(oldMsg.getFromUserName());
            //哪个联系人或群组做的撤回
            String nickName = contactEntity.getNickName();
            //撤回的消息内容
            String revokeMsg = oldMsg.getContent();
            //备注名
            String remarkName = contactEntity.getRemarkName();
            if (oldMsg.getFromUserName().contains("@@")) {
                //群聊中撤回消息用户username
                String groupChatRevokeUserName = MatcheHelper.matches("(.*):<br/>", revokeMsg);
                revokeMsg = MatcheHelper.matches("<br/>(.*)", revokeMsg);
                if (Login.getContactThreadLocal().containsKey(groupChatRevokeUserName)) {
                    contactEntity = Login.getContactThreadLocal().get(groupChatRevokeUserName);
                    remarkName = contactEntity.getRemarkName();
                }
                //如果是群聊
                revokeMsgContent.append("群聊: ");
            }
            //撤回用户名
            String revokeUserName = MatcheHelper.matches("\\<\\!\\[CDATA\\[(?<text>[^\\]]*)\\]\\]\\>", content);
            revokeMsgContent.append(nickName)
                    .append(" || ")
                    .append("撤回人: " + revokeUserName)
                    .append(" || 撤回消息内容为: \r\n")
                    .append(revokeMsg);
            WechatHelper.sendMsg(revokeMsgContent.toString(),oldMsg.getMsgType(), WeChatContactConstants.FILE_HELPER);
        }
        //TODO 将过期信息删除，节省空间

    }
}
