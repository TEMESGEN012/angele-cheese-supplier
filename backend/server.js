const express = require('express');
const cors = require('cors');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// --- In-Memory Mock Data ---
let products = [
    { id: "1", name: "Fresh Milk", price: 50.0, category: "Milk", currentStock: 100, unit: "Liters" },
    { id: "2", name: "Premium Cheese", price: 200.0, category: "Cheese", currentStock: 50, unit: "Kg" }
];

let orders = [];
let users = [];

// ==========================================
// PRODUCTS ENDPOINTS
// ==========================================

app.get('/products', (req, res) => {
    res.json(products);
});

app.post('/products', (req, res) => {
    const product = req.body;
    // Generate a simple ID if not provided
    if (!product.id) product.id = Date.now().toString();
    products.push(product);
    
    // Broadcast real-time update
    io.emit('product_added', product);
    
    res.status(201).json(product);
});

app.put('/products/:id', (req, res) => {
    const { id } = req.params;
    const index = products.findIndex(p => p.id === id);
    if (index !== -1) {
        products[index] = { ...products[index], ...req.body };
        
        // Broadcast real-time update
        io.emit('product_updated', products[index]);
        
        res.json(products[index]);
    } else {
        res.status(404).json({ message: "Product not found" });
    }
});

app.delete('/products/:id', (req, res) => {
    const { id } = req.params;
    products = products.filter(p => p.id !== id);
    res.status(204).send();
});

// ==========================================
// ORDERS ENDPOINTS
// ==========================================

app.get('/orders', (req, res) => {
    res.json(orders);
});

app.post('/orders', (req, res) => {
    const order = req.body;
    if (!order.id) order.id = Date.now(); // Generate numeric ID
    order.status = "Pending";
    orders.push(order);
    
    // Broadcast real-time order alert to admins
    io.emit('order_placed', order);
    
    res.status(201).json(order);
});

app.put('/orders/:id/status', (req, res) => {
    const { id } = req.params;
    const { status } = req.body;
    const index = orders.findIndex(o => o.id === parseInt(id));
    
    if (index !== -1) {
        orders[index].status = status;
        
        // Broadcast real-time status update to the customer
        io.emit('order_updated', orders[index]);
        
        res.json(orders[index]);
    } else {
        res.status(404).json({ message: "Order not found" });
    }
});

app.delete('/orders/:id', (req, res) => {
    const { id } = req.params;
    orders = orders.filter(o => o.id !== parseInt(id));
    res.status(204).send();
});

// ==========================================
// USERS / AUTH ENDPOINTS
// ==========================================

app.post('/users/register', (req, res) => {
    const user = req.body;
    if (!user.id) user.id = Date.now().toString();
    users.push(user);
    res.status(201).json(user);
});

app.post('/users/login', (req, res) => {
    const { email, password } = req.body; // Map<String, String> from Android
    const user = users.find(u => u.email === email && u.password === password);
    
    if (user) {
        res.json(user);
    } else {
        res.status(401).json({ message: "Invalid credentials" });
    }
});

// Start the server (using our wrapped HTTP server to bind Socket.IO)
io.on('connection', (socket) => {
    console.log('Real-time connection opened from client:', socket.id);
    socket.on('disconnect', () => {
        console.log('Client disconnected:', socket.id);
    });
});

server.listen(PORT, () => {
    console.log(`Dairy Flow API Server is running on http://localhost:${PORT}`);
    console.log(`Real-Time WebSockets (Socket.IO) are active.`);
    console.log(`Use this URL + "/api/" if you integrate it completely.`);
});
