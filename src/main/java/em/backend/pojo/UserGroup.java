package em.backend.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user_group")
public class UserGroup {
    @TableId
    private String openId;
    private String chatId;
    
    private Date createTime;
    
    private Date updateTime;
    
    @TableLogic
    private Integer deleted;
} 