@Getter
@Setter
@Entity
public class ChatRoom {
    @Id
    @GeneratedValue
    private UUID chatRoomId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String roomName;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isKino; // RoomType 대신 kino 여부만 체크

    @Column(columnDefinition = "VARCHAR(55)")
    private String familyType;

    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private java.util.Date createdAt;

    @Column(columnDefinition = "VARCHAR(255)")
    private String image;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "familyId", nullable = true)
    private Family family;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL)
    private List<UserChatRoom> userChatRooms;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL)
    private List<Message> messages = new ArrayList<>();
}