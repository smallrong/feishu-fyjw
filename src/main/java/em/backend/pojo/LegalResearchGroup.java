package em.backend.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("legal_research_group")
public class LegalResearchGroup {

    private String difyGroupId;

    private Long caseId;
    
    private String openId;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
} 