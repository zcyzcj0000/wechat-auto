package xyz.ipurple.wechat.handler;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;
import xyz.ipurple.wechat.base.constants.WeChatContactConstants;
import xyz.ipurple.wechat.base.constants.WechatMsgConstants;
import xyz.ipurple.wechat.base.core.init.ContactEntity;
import xyz.ipurple.wechat.base.core.revoke.RevokeMsgInfo;
import xyz.ipurple.wechat.base.core.sync.msg.MsgEntity;
import xyz.ipurple.wechat.base.util.MatcheHelper;
import xyz.ipurple.wechat.base.util.WechatHelper;
import xyz.ipurple.wechat.login.UserContext;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author: zcy
 * @Description:
 * @Date: 2018/8/14 20:10
 * @Modified By:
 */
public class WechatMsgHandler {
    private static final Logger logger = Logger.getLogger(WechatMsgHandler.class);

    private static BlockingQueue<MsgEntity> BLOCKING_QUEUE = new LinkedBlockingQueue<>(50);

    static {
        new Thread(() -> {
            while (true) {
                try {
                    logger.info("从队列中获取撤回消息");
                    MsgEntity msgEntity = BLOCKING_QUEUE.poll(5, TimeUnit.SECONDS);
                    if (null == msgEntity && !revokeMsgHandler(msgEntity)) {
                        logger.info("消息容器中未发现待撤回消息，稍后继续重试");
                        BLOCKING_QUEUE.offer(msgEntity, 5, TimeUnit.SECONDS);
                    } else {
                        logger.info("从队列中获取撤回消息成功");
                    }
                } catch (InterruptedException e) {
                    logger.info("从队列中获取撤回消息失败", e);
                }
            }
        }).start();
    }


    /**
     * 撤回消息处理
     * @param msgEntity msgEntity
     */
    public static Boolean revokeMsgHandler(MsgEntity msgEntity) {
        RevokeMsgInfo revokeMsgInfo = getRevokeMsgInfo(msgEntity);
        //防止消息未到达，撤回消息先到达，导致获取撤回消息失败。
        if (null == revokeMsgInfo) {
            return false;
        }

        int msgType = revokeMsgInfo.getMsgType();
        if (revokeMsgInfo != null) {
            if (msgType == WechatMsgConstants.TEXT_MSG) {                    //普通文本消息
                StringBuffer revokeMsgContent = getPreContentForRevokeMsg(revokeMsgInfo).append(revokeMsgInfo.getContent());
                WechatHelper.sendTextMsg(revokeMsgContent.toString(), WeChatContactConstants.FILE_HELPER);
            } else if (msgType == WechatMsgConstants.IMAGE_FILE_MSG) {       //图片文件消息
                StringBuffer revokeMsgContent = getPreContentForRevokeMsg(revokeMsgInfo).append("图片文件");
                WechatHelper.sendTextMsg(revokeMsgContent.toString(), WeChatContactConstants.FILE_HELPER);
                WechatHelper.sendImageFileMsg(revokeMsgInfo.getContent(), WeChatContactConstants.FILE_HELPER);
            } else if (msgType == WechatMsgConstants.IMAGE_EXPRESSION_MSG) { //图片表情消息
                StringBuffer revokeMsgContent = getPreContentForRevokeMsg(revokeMsgInfo).append("图片表情");
                WechatHelper.sendTextMsg(revokeMsgContent.toString(), WeChatContactConstants.FILE_HELPER);
                WechatHelper.sendEmoticonMsg(revokeMsgInfo.getContent(), WeChatContactConstants.FILE_HELPER);
            } else if (msgType == WechatMsgConstants.VOICE_MSG) {               //语音消息
//                StringBuffer revokeMsgContent = getPreContentForRevokeMsg(revokeMsgInfo).append("语音消息:");
//                WechatHelper.sendTextMsg(revokeMsgContent.toString(), WeChatContactConstants.FILE_HELPER);
//                WechatHelper.sendImageFileMsg(revokeMsgInfo.getContent(), WeChatContactConstants.FILE_HELPER);
            } else if (msgType == WechatMsgConstants.FILE_MSG) {                //文件
            } else if (msgType == 0) {                                           //短视频

            }
        }
        return true;
    }

    //获取撤回人信息
    public static RevokeMsgInfo getRevokeMsgInfo(MsgEntity msgEntity) {
        String content = StringEscapeUtils.unescapeXml(msgEntity.getContent());
        //匹配撤回的消息id
        String revokeMsgid = MatcheHelper.matches("<msgid>(.*)</msgid>", content);
        String replaceMsg = MatcheHelper.matches("<replacemsg>(.*)</replacemsg>", content);
        //从消息列表中查找到撤回的消息
        MsgEntity oldMsg = UserContext.getMsgThreadLocal().get(Long.valueOf(revokeMsgid));
        //获取撤回消息相关信息
        if (oldMsg != null && UserContext.getContactThreadLocal().containsKey(oldMsg.getFromUserName())) {
            RevokeMsgInfo revokeMsgInfo = new RevokeMsgInfo();
            //获取联系人信息
            ContactEntity contactEntity = UserContext.getContactThreadLocal().get(oldMsg.getFromUserName());
            //哪个联系人或群组做的撤回
            String nickName = contactEntity.getNickName();
            //撤回的消息内容
            String revokeMsg = oldMsg.getContent();
            //撤回用户名
            String revokeUserName = MatcheHelper.matches("\\<\\!\\[CDATA\\[(?<text>[^\\]]*)\\]\\]\\>", content);
            //如果是图片表情 截取md5值
            if (oldMsg.getMsgType() == WechatMsgConstants.IMAGE_EXPRESSION_MSG) {
                revokeMsg = MatcheHelper.matches("md5=\"(.*?)\"", revokeMsg);
            } else if (oldMsg.getFromUserName().contains("@@")) {
                //群聊中撤回消息用户username
                revokeMsg = MatcheHelper.matches("<br/>(.*)", revokeMsg);
                revokeMsgInfo.setChatGroupFlag(1);
            }

            revokeMsgInfo.setNickName(nickName);
            revokeMsgInfo.setRevokeUserName(revokeUserName);
            revokeMsgInfo.setContent(revokeMsg);
            revokeMsgInfo.setMsgType(oldMsg.getMsgType());
            return revokeMsgInfo;
        }
        return null;
    }

    /**
     * 获取要发送的撤回消息前缀内容
     * @param revokeMsgInfo
     * @return
     */
    private static StringBuffer getPreContentForRevokeMsg(RevokeMsgInfo revokeMsgInfo) {
        StringBuffer revokeMsgContent = new StringBuffer();
        if (1 == revokeMsgInfo.getChatGroupFlag()) {
            revokeMsgContent.append("群聊");
        }
        revokeMsgContent.append("昵称: ")
                .append(revokeMsgInfo.getNickName())
                .append("\r\n撤回人: " + revokeMsgInfo.getRevokeUserName())
                .append("\r\n撤回消息内容为: \r\n");
        return revokeMsgContent;
    }
}
