import random, datetime as dt
random.seed(7)
NAMES=[('무선 이어폰','🎧',129000),('니트 가디건','🧶',59000),('캠핑 의자','🪑',45000),('향초 세트','🕯️',28000),('러닝화','👟',119000),
('텀블러','🥤',32000),('데스크 램프','💡',58000),('블루투스 스피커','🔊',89000),('린넨 셔츠','👕',39000),('핸드크림','🧴',15000),
('스마트워치 밴드','⌚',22000),('여행 파우치','👝',18000),('원목 트레이','🍽️',26000),('미니 가습기','💨',34000),('캔버스 토트백','👜',42000),
('무드 조명','🌙',48000),('노이즈캔슬링 헤드폰','🎧',329000),('기계식 키보드','⌨️',159000),('필름 카메라','📷',210000),('요가 매트','🧘',36000),
('커피 드리퍼','☕',27000),('실버 반지','💍',68000),('보조배터리','🔋',35000),('모니터 받침대','🖥️',49000),('양털 슬리퍼','🥿',29000),
('헤어 드라이어','💨',89000),('데일리 백팩','🎒',58000),('티셔츠 3팩','👕',33000),('디퓨저','🌿',31000),('책 3권','📚',45000),
('무선 충전기','🔌',39000),('선글라스','🕶️',79000),('에어프라이어','🍳',99000),('후드집업','🧥',69000),('식물 화분','🪴',25000),
('전자책 리더기','📖',189000),('주방 칼','🔪',55000)]
# (day, count) clusters
plan=[(2,2),(4,1),(6,3),(9,1),(13,7),(14,3),(16,2),(19,2),(23,2),(26,6),(27,5),(29,2),(30,1)]
items=[];k=0
today=29
for day,c in plan:
    for j in range(c):
        n,e,p=NAMES[k%len(NAMES)];k+=1
        if day<=21: st=random.choices(['tree','hole'],[0.62,0.38])[0]
        elif day<=27: st=random.choices(['tree','hole','sprout'],[0.35,0.2,0.45])[0]
        else: st='mound'
        if p is not None and random.random()<0.06: p=None
        items.append(dict(id=k,day=day,name=n,emoji=e,price=p,state=st))
def asset(it):
    s=it['state']
    if s=='tree':
        p=it['price']
        if p is None: return 'prop_tree_small'
        return 'prop_tree_small' if p<50000 else ('prop_tree_big' if p<200000 else 'prop_tree_fruit')
    return {'hole':'prop_hole','sprout':'prop_sprout_ready','mound':'prop_acorn_buried'}[s]
WEEKS=[(1,'9월 1주','9.1 – 9.7',1,7),(2,'9월 2주','9.8 – 9.14',8,14),(3,'9월 3주','9.15 – 9.21',15,21),(4,'9월 4주','9.22 – 9.28',22,28),(5,'9월 5주','9.29 – 10.5',29,30)]
def summ(its):
    t=[i for i in its if i['state']=='tree'];h=[i for i in its if i['state']=='hole']
    return dict(tree=len(t),saved=sum(i['price'] or 0 for i in t),hole=len(h),spent=sum(i['price'] or 0 for i in h),
        sprout=sum(i['state']=='sprout' for i in its),mound=sum(i['state']=='mound' for i in its))
won=lambda v:f"{v:,}원"
