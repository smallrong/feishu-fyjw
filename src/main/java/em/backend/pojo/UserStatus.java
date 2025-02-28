package em.backend.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_status")
public class UserStatus {
    
    @TableId
    private String openId;
    
    private Long currentCaseId;
    
    private String currentCaseName;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
} 