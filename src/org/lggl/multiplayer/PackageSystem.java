package org.lggl.multiplayer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.lggl.utils.LGGLException;

public class PackageSystem {

	public static PackageSession connect(InetAddress server, int port) throws LGGLException {
		try {
			Socket socket = new Socket(server, port);
			PackageSession session = new PackageSession(socket);
			session.connect();
			return session;
		} catch (IOException e) {
			throw new LGGLException("Network I/O Error", e);
		}
		
	}
	
	public static PackageServer createServer(int port) throws LGGLException {
		try {
			ServerSocket server = new ServerSocket(port);
			PackageServer srv = new PackageServer(server);
			return srv;
		} catch (IOException e) {
			throw new LGGLException("Network I/O Error", e);
		}
	}
	
}