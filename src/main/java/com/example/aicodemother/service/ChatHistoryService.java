package com.example.aicodemother.service;

import com.example.aicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.example.aicodemother.model.entity.ChatHistory;
import com.example.aicodemother.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/Kenneth0111">程序员张博洋</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加一条对话历史
     *
     * @param appId       应用 id
     * @param message     消息内容
     * @param messageType 消息类型（user/ai）
     * @param userId      创建用户 id
     * @return 是否保存成功
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用 id 删除对话历史（用于删除应用时级联删除）
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 分页查询某个应用的对话历史（游标分页，仅创建者或管理员可见）
     *
     * @param appId          应用 id
     * @param pageSize       每页大小
     * @param lastCreateTime 上次查询到的最早一条消息的创建时间（用于向前加载更多）
     * @param loginUser      当前登录用户
     * @return 对话历史分页结果（按 createTime 降序）
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser);

    /**
     * 根据查询条件构造查询参数（管理员）
     *
     * @param chatHistoryQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
