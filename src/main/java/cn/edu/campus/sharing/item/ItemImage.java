package cn.edu.campus.sharing.item;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("item_images")
public class ItemImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;
    private String filePath;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
