typedef unsigned int u32;
typedef unsigned char u8;

typedef struct contour_node {
	struct contour_node* next;
	u32 x;
	u32 y;
	struct contour_node* prev;
} contour_node_t;

typedef point {
	u32 x;
	u32 y;
} point_t;

struct contour_args {
	struct contour_node* node0;
	u32 img_width;
	u32 img_height;
	u32 img_length;
	float red_factor;
	float green_factor;
	float blue_factor;
	u8 threshold;
	u8* gsc_img;
	u8* rgb_img;
};

void contour_node_compress(contour_node_t* node0) {
	contour_node_t* node_A = node0, node_B = node_A->next, node_C = node_B->next;
	float slope;
	do {
		if ((!node_B) || (!node_C))
			return;
		slope = ((float)(node_C->y - node_A->y)/((float)(node_C->x - node_A->x));
		if (node_B->y)
	} while ((node_A = node_B) && (node_B = node_C) && (node_C = node_C->next) && (nodeA != node0));
}

u32 contour_node_toArray(contour_node_t* node, point_t** result) {
	contour_node_t* start_node = node;
	compress_nodes(node0);
	size_t num_nodes = getLastNode(&end_node, 65535);
	*result = calloc(num_nodes, sizeof(point_t));
	while (node typedef unsigned int u32;
typedef unsigned char u8;

typedef struct contour_node {
	struct contour_node* next;
	u32 x;
	u32 y;
	struct contour_node* prev;
} contour_node_t;
typedef point {
	u32 x;
	u32 y;
} point_t;
struct contour_args {
	struct contour_node* node0;
	u32 img_width;
	u32 img_height;
	u32 img_length;
	float red_factor;
	float green_factor;
	float blue_factor;
	u8 threshold;
	u8* gsc_img;
	u8* rgb_img;
};
u32 contour_node_compress(contour_node_t* node0) {
	contour_node_t* node_A = node0, node_B = node_A->next, node_C = node_B->next;
	float slope;
	int ignore_node0 = 1;
	u32 nodes_removed = 0;
	while ((node_A != node0 || ignore_node0) && node_C) {
		if ((!node_B) || (!node_C))
			return;
		if (((float)(node_B->y - node_A->y))/((float)(node_B->x - node_A->x)) == ((float)(node_C->y - node_A->y))/((float)(node_C->x - node_A->x))) {
			free(node_B);
			nodes_removed++;
			node_B = node_C;
			node_A->next = node_B;
			node_B->prev = node_A;
			node_C = node_C->next;
		} else {
			node_A = node_B;
			node_B = node_C;
			node_C = node_C->next;
			ignore_node0 = 0;
		}
	};
}

size_t contour_node_toArray(contour_node_t* node, point_t** result) {
	contour_node_t* start_node = node;
	contour_node_t* end_node = node;
	compress_nodes(node0);
	size_t num_nodes = getLastNode(&end_node, 65535);
	*result = calloc(num_nodes, sizeof(point_t));
	point_t** result_ptr = result;
	while (*(result_ptr++) = (node = node->next) && node != start_node && node != end_node);
	return num_nodes;
}

void contour_node_remove(contour_node_t* current) {
	if (current == NULL)
		return;
	contour_node_t* prev = current->prev;
	contour_node_t* next = current->next;
	if (prev != NULL && next != NULL) {
		prev->next = next;
		next->prev = prev;
	} else if (prev != NULL) {
		prev->next = NULL;
	} else if (next != NULL) {
		next->prev = NULL;
	}
	free(current);
}

contour_node_t* contour_node_skip(contour_node_t* node, size_t skip) {
	while (skip-- && node->next)
		node = node->next;
	return node;
}

size_t contour_node_getLast(contour_node_t** node, size_t maxSearch) {
	contour_node_t* start_node = *node;
	if (start_node == NULL)
		return 0;
	
	contour_node_t* next_node;
	size_t i = 0;
	while ((next_node = (*node)->next) != NULL && next_node != start_node) {
		*node = next_node;
		if (i++ > maxSearch)
			return i;
	}
	return i;
}
