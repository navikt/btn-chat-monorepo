function createLabeledFormElement(labelContent, type = 'input', containerType = 'form') {
    const name = labelContent.replace(/\s/g, '').toLowerCase();
    const label = document.createElement('label');
    label.htmlFor = `${name}`;
    label.innerText = labelContent;
    label.classList.add("formelement-label");

    const formElement = document.createElement(type);
    formElement.name = name;
    formElement.id = name;

    const container = document.createElement(containerType);
    container.classList.add(`${name}-container`);
    container.appendChild(label);
    container.appendChild(formElement);

    return {container, label, formElement};
}

function setup() {
    const app = document.querySelector('.chatapp');
    const roomInput = createLabeledFormElement('Join room', 'input');
    const joinButton = document.createElement('button');
    joinButton.innerText = 'Join';
    roomInput.container.appendChild(joinButton);

    const textarea = createLabeledFormElement('ChatRoom', 'textarea', 'div');
    const messageInput = createLabeledFormElement('Message', 'input');
    const sendButton = document.createElement('button');
    sendButton.innerText = 'Send';
    messageInput.container.appendChild(sendButton);

    app.appendChild(roomInput.container);
    app.appendChild(textarea.container);
    app.appendChild(messageInput.container);

    roomInput.container.addEventListener('submit', (e) => {
        e.preventDefault();
        joinRoom(e.target.elements.joinroom.value);
    });

    messageInput.container.addEventListener('submit', (e) => {
        e.preventDefault();
        sendMessage(e.target.elements.message.value);
    });

    return {textarea: textarea.formElement, message: messageInput.formElement};
}

const {textarea, message} = setup();
let ws = null;
let currentRoom = null;

function processWSMessage(wsMessage) {
    console.log('process')
    switch (wsMessage.type) {
        case 'JOINED': return `${wsMessage.ident} joined chatroom.`;
        case 'LEFT': return `${wsMessage.ident} left chatroom.`;
        case 'MESSAGE': return `${wsMessage.ident}: ${wsMessage.content}`;
    }
    return null;
}

function joinRoom(room) {
    if (!room || room === currentRoom) {
        return;
    }
    if (ws) {
        ws.close();
    }
    ws = new WebSocket(`ws://localhost:7075/btn-chat-fss/api/chat/${room}`);
    ws.addEventListener('open', function () {
        textarea.value += `Joined ${room}\n`;
        currentRoom = room;
    });

    ws.addEventListener('message', function (event) {
        console.log('content', JSON.parse(event.data));
        const newContent = processWSMessage(JSON.parse(event.data));
        if (newContent) {
            textarea.value += `${newContent}\n`;
        }
    });

    ws.addEventListener('close', function (e) {
        console.log('WS closed', e);
        textarea.value += `Connection lost to ${room}\n`;
        currentRoom = null;
    });
    ws.addEventListener('error', function (e) {
        console.log('WS error', e);
        textarea.value += `Connection lost to ${room}\n`;
        currentRoom = null;
    });
}

function sendMessage(text) {
    if (!ws || !currentRoom || !text) {
        return;
    }

    console.log('sending', text);
    ws.send(text);
    message.value = '';
}
