package org.example.spring;

public class ButtonDTO {
    private String buttonData;
    private Long chatId;

    public ButtonDTO(String buttonData, Long chatId) {
        this.buttonData = buttonData;
        this.chatId = chatId;
    }

    public String getButtonData() {
        return buttonData;
    }

    public void setButtonData(String buttonData) {
        this.buttonData = buttonData;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
}
