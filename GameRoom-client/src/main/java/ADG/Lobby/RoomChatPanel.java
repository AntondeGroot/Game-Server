package ADG.Lobby;

import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.HeadingElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.*;

import java.util.List;

/**
 * The room chat: a message list with an input row. On a wide screen it is a side
 * column; on a phone it is a bottom sheet the room view opens and closes.
 */
public class RoomChatPanel extends Composite {

    interface Binder extends UiBinder<Widget, RoomChatPanel> {}
    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField HeadingElement title;
    @UiField Button closeButton;
    @UiField FlowPanel messageList;
    @UiField TextBox messageInputField;
    @UiField Button sendButton;

    public RoomChatPanel() {
        initWidget(BINDER.createAndBindUi(this));
        title.setInnerText(I18n.c().chat());
        closeButton.setHTML(RoomIcons.CROSS);
        closeButton.getElement().setAttribute("aria-label", I18n.c().closeChat());
        sendButton.setHTML(RoomIcons.SEND);
        sendButton.getElement().setAttribute("aria-label", I18n.c().send());
        messageInputField.getElement().setAttribute("placeholder", I18n.c().messagePlaceholder());
        messageInputField.getElement().setAttribute("aria-label", I18n.c().messagePlaceholder());
    }

    public Button getCloseButton() { return closeButton; }
    public Button getSendButton() { return sendButton; }
    public TextBox getMessageInputField() { return messageInputField; }

    /**
     * Replaces the shown messages. Messages sent under {@code ownName} sit on the right.
     * Stays scrolled to the newest message unless the reader scrolled up to read back.
     */
    public void showMessages(List<Message> messages, String ownName) {
        Element list = messageList.getElement();
        boolean atBottom = isScrolledToBottom(list);
        messageList.clear();
        for (Message message : messages) {
            messageList.add(bubble(message, message.getNameSender().equals(ownName)));
        }
        if (atBottom) scrollToBottom(list);
    }

    private static Widget bubble(Message message, boolean own) {
        FlowPanel meta = new FlowPanel();
        meta.setStyleName("chat-msg-meta");
        if (!own) meta.add(styled(new InlineLabel(message.getNameSender()), "chat-msg-sender"));
        meta.add(new InlineLabel(message.getTimestamp()));

        FlowPanel bubble = new FlowPanel();
        bubble.setStyleName(own ? "chat-msg chat-msg-own" : "chat-msg");
        bubble.add(meta);
        bubble.add(styled(new Label(message.getMessage()), "chat-msg-text"));
        return bubble;
    }

    private static Widget styled(Widget widget, String styleName) {
        widget.setStyleName(styleName);
        return widget;
    }

    private native boolean isScrolledToBottom(Element el) /*-{
        return el.scrollHeight - el.scrollTop - el.clientHeight < 6;
    }-*/;

    private native void scrollToBottom(Element el) /*-{
        el.scrollTop = el.scrollHeight;
    }-*/;
}
