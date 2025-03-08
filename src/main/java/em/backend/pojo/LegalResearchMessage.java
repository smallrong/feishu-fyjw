package em.backend.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("legal_research_message")
public class LegalResearchMessage {
    
    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;
    
    private String groupId;
    
    private String userMessage;
    
    private String assistantMessage;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
} 