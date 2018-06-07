from socket import *
import sys

# check number of args
if len(sys.argv) != 5:
	raise ValueError("invalid number of input arguments")

server_address = sys.argv[1]
# check if numerical input is valid
try:
    n_port = int(sys.argv[2])
except ValueError:
    raise ValueError("invalid input type for n_port")   
try:
	req_code = int(sys.argv[3])
except ValueError:
	raise ValueError("invalid input type for req_code")
msg = sys.argv[4]

# initiate TCP connection at SERVER_PORT, send req_code to the server and retrieve data transmission port
clientSocket = socket(AF_INET, SOCK_STREAM)
clientSocket.connect((server_address, n_port))
clientSocket.send((str(req_code)).encode())
r_port = clientSocket.recv(1024)
clientSocket.close()

# send input to server through data transmission port via UDP and retrieve output
if r_port != '':
	clientSocket = socket(AF_INET, SOCK_DGRAM)
	clientSocket.sendto(msg.encode(), (server_address, int(r_port)))
	modified_msg, server_address = clientSocket.recvfrom(1024)
	print(modified_msg.decode())
	clientSocket.close()
