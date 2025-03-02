package em.backend.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("case_info")
public class CaseInfo {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String openId;
    
    private String caseName;
    
    private String clientName;
    
    private String caseDesc;
    
    private LocalDateTime caseTime;
    
    private String remarks;
    
    private String folderUrl;

    private String difyKnowledgeId;
    
    
    private LocalDateTime createTime;
    
    
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
} 