-- 创建用户案件信息表
CREATE TABLE case_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '案件基本信息id',
    open_id VARCHAR(100) NOT NULL COMMENT '平台用户标识openid(飞书内)',
    case_name VARCHAR(255) NOT NULL COMMENT '案件名称',
    client_name VARCHAR(255) NOT NULL COMMENT '当事人',
    case_desc TEXT COMMENT '案情简介',
    case_time DATETIME NOT NULL COMMENT '接案时间',
    remarks TEXT COMMENT '备注',
    folder_url VARCHAR(500) COMMENT '文件夹链接地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-未删除，1-已删除',
    INDEX idx_open_id (open_id),
    INDEX idx_case_name (case_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户案件信息表';

-- 创建用户当前状态表
CREATE TABLE user_status (
    open_id VARCHAR(100) PRIMARY KEY COMMENT '平台用户标识openid(飞书内)',
    current_case_id BIGINT COMMENT '当前选择案件id',
    current_case_name VARCHAR(255) COMMENT '当前选择案件名称',
    current_legal_research_group_id BIGINT COMMENT '当前法律研究对话组id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-未删除，1-已删除',
    INDEX idx_current_case_id (current_case_id),
    INDEX idx_current_legal_research_group_id (current_legal_research_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户当前状态表';

-- 创建用户当前群组表
CREATE TABLE user_group (
    open_id VARCHAR(100) PRIMARY KEY COMMENT '平台用户标识openid(飞书内)',
    chat_id VARCHAR(100) NOT NULL COMMENT '飞书群组id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-未删除，1-已删除',
    UNIQUE INDEX idx_chat_id (chat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户当前群组表（用于维护Dify对话上下文）';

-- 创建法律研究对话组表
CREATE TABLE legal_research_group (
    group_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '法律研究对话组id',
    case_id BIGINT NOT NULL COMMENT '关联的案件id',
    open_id VARCHAR(100) NOT NULL COMMENT '创建用户的openid',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-未删除，1-已删除',
    INDEX idx_case_id (case_id),
    INDEX idx_open_id (open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律研究对话组表';

-- 创建法律研究消息表
CREATE TABLE legal_research_message (
    message_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '法律研究对话消息id',
    group_id BIGINT NOT NULL COMMENT '法律研究对话组id',
    user_message TEXT COMMENT '用户消息内容',
    assistant_message TEXT COMMENT '助手消息内容',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-未删除，1-已删除',
    INDEX idx_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律研究消息表';

