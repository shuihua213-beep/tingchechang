package com.cf.forward.dh.common;

import com.cf.forward.dh.lib.ToolKits;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/*
 * 登陆面板
 */
public class LoginPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	
	//登陆参数
	private String s_strIp		   = System.getenv("CF_FORWARD_DH_CAMERA_IP") == null ? "127.0.0.1" : System.getenv("CF_FORWARD_DH_CAMERA_IP");
	private Integer s_nPort        = Integer.valueOf(System.getenv("CF_FORWARD_DH_CAMERA_PORT") == null ? "37777" : System.getenv("CF_FORWARD_DH_CAMERA_PORT"));
	private String s_strUser       = System.getenv("CF_FORWARD_DH_CAMERA_USERNAME") == null ? "admin" : System.getenv("CF_FORWARD_DH_CAMERA_USERNAME");
	private String s_strPassword   = System.getenv("CF_FORWARD_DH_CAMERA_PASSWORD") == null ? "admin123" : System.getenv("CF_FORWARD_DH_CAMERA_PASSWORD");
	
	public LoginPanel() {
		BorderEx.set(this, Res.string().getLogin(), 2);
		setLayout(new FlowLayout());

		////////////////////////////////
		loginBtn = new JButton(Res.string().getLogin());
		logoutBtn = new JButton(Res.string().getLogout());
		ipLabel = new JLabel(Res.string().getDeviceIp());
		portLabel = new JLabel(" " + Res.string().getPort());
		nameLabel = new JLabel(" " + Res.string().getUserName());
		passwordLabel = new JLabel(" " + Res.string().getPassword());
		ipTextArea = new JTextField(s_strIp);
		nameTextArea = new JTextField(s_strUser);
		passwordTextArea = new JPasswordField(s_strPassword);
		portTextArea = new JTextField(s_nPort.toString());
	    
	    add(ipLabel);
	    add(ipTextArea);
	    add(portLabel);
	    add(portTextArea);
	    add(nameLabel);
	    add(nameTextArea);
	    add(passwordLabel);
	    add(passwordTextArea);
	    add(loginBtn);
	    add(logoutBtn);
	    
	    ipTextArea.setPreferredSize(new Dimension(90, 20)); 
	    nameTextArea.setPreferredSize(new Dimension(90, 20));
	    passwordTextArea.setPreferredSize(new Dimension(90, 20));
	    portTextArea.setPreferredSize(new Dimension(90, 20));
		    
	    loginBtn.setPreferredSize(new Dimension(80, 20)); 
	    logoutBtn.setPreferredSize(new Dimension(80, 20)); 
	    ToolKits.limitTextFieldLength(portTextArea, 6);
	    
	    logoutBtn.setEnabled(false);	
	}

	public void addLoginBtnActionListener(ActionListener e) {
		loginBtn.addActionListener(e);
	}
	
	public void addLogoutBtnActionListener(ActionListener e) {
		logoutBtn.addActionListener(e);
	}
	
	public void setButtonEnable(boolean bln) {
		loginBtn.setEnabled(!bln);
		logoutBtn.setEnabled(bln);
	}
	
	public boolean checkLoginText() {
		if(ipTextArea.getText().equals("")) {
			JOptionPane.showMessageDialog(null, Res.string().getInputDeviceIP(), Res.string().getErrorMessage(), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		if(portTextArea.getText().equals("")) {
			JOptionPane.showMessageDialog(null, Res.string().getInputDevicePort(), Res.string().getErrorMessage(), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		if(nameTextArea.getText().equals("")) {
			JOptionPane.showMessageDialog(null, Res.string().getInputUsername(), Res.string().getErrorMessage(), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		if(new String(passwordTextArea.getPassword()).equals("")) {
			JOptionPane.showMessageDialog(null, Res.string().getInputPassword(), Res.string().getErrorMessage(), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		return true;
	}
	
	public JLabel nameLabel;
	public JLabel passwordLabel;
	public JLabel ipLabel;
	public JLabel portLabel;
	
	public JTextField ipTextArea;
	public JTextField portTextArea;
	public JTextField nameTextArea;
	public JPasswordField passwordTextArea;	
	
	public JButton loginBtn;
	public JButton logoutBtn;
}
