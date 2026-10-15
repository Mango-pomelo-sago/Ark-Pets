/** Copyright (c) 2022-2026, Harry Huang, Half Nothing
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.tray;

import cn.harryh.arkpets.concurrent.SocketData;
import cn.harryh.arkpets.concurrent.SocketSession;
import cn.harryh.arkpets.utils.Logger;

import javax.swing.*;


public class MemberTrayProxy extends MemberTray {
    private final SocketSession session;
    private final HostTray hostTray;
    private final JMenu popMenu;

    /** Initializes a host-proxy tray icon instance for a registering ArkPets.
     * @param socketData The ArkPets login data.
     * @param session The session to communicate with that ArkPets.
     * @param hostTray The host tray to apply this tray instance.
     */
    public MemberTrayProxy(SocketData socketData, SocketSession session, HostTray hostTray) {
        super(socketData.getMsgString());
        this.session = session;
        this.hostTray = hostTray;

        // Ui Components:
        JLabel innerLabel = new JLabel(" " + name + " ");
        innerLabel.setAlignmentX(0.5f);

        popMenu = new JMenu(name);
        popMenu.add(innerLabel);
        popMenu.add(optKeepAnimEn);
        popMenu.add(optTransparentEn);
        popMenu.add(optEfficiency);
        popMenu.add(optExit);
        popMenu.setSize(100, 24 * popMenu.getSubElements().length);

        hostTray.addMemberTray(popMenu);
    }

    public void onCanChangeStage() {
        Logger.info("ProxyTray", "Can change stage");
        popMenu.add(optChangeStage, 3);
    }

    @Override
    public void onExit() {
        Logger.info("ProxyTray", "Request to exit");
        remove();
    }

    @Override
    public void onChangeStage() {
        Logger.info("ProxyTray", "Request to change stage");
        popMenu.remove(optKeepAnimDis);
        popMenu.add(optKeepAnimEn, 1);
    }

    @Override
    public void onTransparentDis() {
        Logger.info("ProxyTray", "Transparent disabled");
        popMenu.remove(optTransparentDis);
        popMenu.add(optTransparentEn, 2);
    }

    @Override
    public void onTransparentEn() {
        Logger.info("ProxyTray", "Transparent enabled");
        popMenu.remove(optTransparentEn);
        popMenu.add(optTransparentDis, 2);
    }

    @Override
    public void onKeepAnimDis() {
        Logger.info("ProxyTray", "Action-Mode disabled");
        popMenu.remove(optKeepAnimDis);
        popMenu.add(optKeepAnimEn, 1);
    }

    @Override
    public void onKeepAnimEn() {
        Logger.info("ProxyTray", "Action-Mode enabled");
        popMenu.remove(optKeepAnimEn);
        popMenu.add(optKeepAnimDis, 1);
    }

    @Override
    public void sendOperation(SocketData.Operation operation) {
        session.send(SocketData.ofOperation(uuid, operation));
    }

    @Override
    public void onEfficiency() {
        Logger.info("ProxyTray", "Request to show efficiency module");
        // 这个菜单本来就长在启动器自己的托盘里，切页就地完成即可，不必再往桌宠那边绕一趟 socket
        // （那条路最后也是回到这里）。
        hostTray.showEfficiency();
    }

    @Override
    public void showTip(String text) {
        Logger.debug("ProxyTray", "Forwarding tip: " + text);
        session.send(SocketData.ofOperation(uuid, SocketData.Operation.SHOW_TIP, text));
    }

    @Override
    public void remove() {
        hostTray.removeMemberTray(popMenu);
    }
}
