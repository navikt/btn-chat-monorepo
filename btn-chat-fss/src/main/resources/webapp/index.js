Date.prototype.toZISOString = function() {
    return this.getUTCFullYear() +
        '-' + (this.getMonth() + 1).toString().padStart(2, '0') +
        '-' + this.getDate().toString().padStart(2, '0') +
        'T' + this.getHours().toString().padStart(2, '0') +
        ':' + this.getMinutes().toString().padStart(2, '0') +
        ':' + this.getSeconds().toString().padStart(2, '0') +
        '.' + (this.getMilliseconds() / 1000).toFixed(3).slice(2, 5) +
        'Z';
};

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

const messages = [];
const {textarea, message} = setup();
let ws = null;
let currentRoom = null;

function processWSMessage(wsMessage) {
    switch (wsMessage.eventType) {
        case 'CONNECTED': return `${wsMessage.actorId.value} joined chatroom.`;
        case 'DISCONNECTED': return `${wsMessage.actorId.value} left chatroom.`;
        case 'MESSAGE': return `${wsMessage.actorId.value}: ${wsMessage.eventData}`;
        case 'RAW': return wsMessage.content;
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
    const location = document.location;
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    ws = new WebSocket(`${protocol}://${location.host}${location.pathname}/api/chat/${room}`);
    ws.addEventListener('open', function () {
        messages.push({ eventType: 'RAW', time: new Date().toZISOString(), content: `Joined ${room}`})
        currentRoom = room;
    });

    ws.addEventListener('message', function (event) {
        const message = JSON.parse(event.data);
        messages.push(message);
        messages.sort((a, b) => a.time.localeCompare(b.time));

        console.log('messages', messages);
        textarea.value = messages
            .map(processWSMessage)
            .join('\n');
    });

    ws.addEventListener('close', function (e) {
        console.log('WS closed', e);
        messages.push({ eventType: 'RAW', time: new Date().toISOString(), content: `Connection lost to ${room}`});
        currentRoom = null;
    });
    ws.addEventListener('error', function (e) {
        console.log('WS error', e);
        messages.push({ eventType: 'RAW', time: new Date().toISOString(), content: `Connection lost to ${room}`});
        currentRoom = null;
    });
}

function sendMessage(text) {
    if (!ws || !currentRoom || !text) {
        return;
    }

    console.log('sending', text);
    ws.send(JSON.stringify({ eventType: 'MESSAGE', content: text }));
    message.value = '';
}
